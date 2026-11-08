# MedAlert — Paciente/Cuidador: pendências, endpoints e testes

> Gerado em 07/11/2026. **Nada foi compilado nem executado** (o ambiente de trabalho não tem Android SDK nem acesso ao Maven/Gradle). Só foi feita checagem de sintaxe dos arquivos alterados. Compile no Android Studio antes de testar.

## 1. Arquivos para colocar no projeto antes de compilar
- `app/google-services.json` (package `com.andre.medalert`). **Sem ele o plugin `google-services` já falha o build.**
- Dependências Firebase: já existiam (`firebase-bom 33.5.0`, `firebase-messaging-ktx`, `firebase-analytics-ktx`) e o plugin `com.google.gms.google-services 4.4.2`. Nada foi adicionado.

## 2. Endpoints usados (contratos reais do projeto)
| Endpoint | Uso |
|---|---|
| `POST /api/users/login`, `POST /api/users`, `POST /api/users/cuidador` | login e cadastros (sem mudança) |
| `GET /api/meds/{pacienteId}` | paciente e cuidador leem os MESMOS medicamentos (cuidador usa `pacienteId`) |
| `POST /api/meds`, `POST /api/meds/edit`, `DELETE /api/meds/{id}` | só paciente |
| `POST /api/historys` `{usuario_id, medicamento_id, tomado_em}` | registro de dose; cuidador envia com `usuario_id` = paciente |
| `GET /api/historys/doseDia/{pacienteId}` | reconciliação de hoje (traz o que o outro aparelho registrou) |
| `GET /api/historys/dashboard/{pacienteId}` | adesão (sem mudança) |
| `POST /api/users/token` `{cuidadorId, token}` | registro do token FCM (**só cuidador**) |
| `POST /api/users/requestCuidador` `{email, nome, userId}` | convite do paciente (agora ligado na tela Responsáveis) |

## 3. O que ainda precisa mudar no BACKEND
1. **Autorização (crítico):** `GET /api/meds/{id}` e `GET /api/historys/*/{id}` aceitam qualquer id (um cuidador sem convite já recebeu medicamento de um paciente). Validar token + vínculo e responder 403.
2. **Login sem token:** o login devolve só `{id, tipo, pacienteId}`. Sem JWT não existe autenticação real; o app já envia `Authorization: Bearer` quando houver `token`.
3. **`POST /api/historys`:** hoje não recebe horário previsto nem id do evento. Necessário:
   - request: `{usuario_id, medicamento_id, previsto_em (ISO), tomado_em, client_event_id (UUID)}`; autor e origem (`paciente`/`cuidador`) derivados do token;
   - dedupe: único `(medicamento_id, previsto_em)`; repetição → **409** ou 200 idempotente (o app trata 409 como "já existe" e confirma a linha — **confirmar**);
   - response: registro canônico `{id, origem, registrado_por, ...}`.
4. **Histórico por período:** `GET /api/historys?usuario_id=&de=&ate=&since=` com `medicamento_id`, `previsto_em`, `tomado_em`, origem, autor, status explícito e `updated_at`. Hoje o cuidador só vê o dia atual e a associação é por nome + dosagem.
5. **"Não tomado" explícito:** hoje é inferido por ausência de registro (>120 min). Precisa de registro/estado explícito no servidor para o requisito de não tratar ausência como "não tomou".
6. **Vínculo:** endpoint para o cuidador listar convites pendentes e para o paciente listar/remover responsáveis; `accept` deve devolver o `pacienteId`. O aceite **não está ligado no Android** (falta o payload do push de convite e a navegação a partir dele).
7. **`requestCuidador`:** confirmar se `nome` é o do paciente (o app envia o do paciente) ou o do cuidador.
8. **Token FCM do paciente:** não existe endpoint (só `cuidadorId`). Sem isso o paciente não recebe "cuidador registrou dose".
9. **Gatilhos de push (nenhum existe hoje, exceto o convite):** enviar mensagem **só de dados** (sem bloco `notification`) com as chaves abaixo.

### Payload FCM esperado pelo app (proposto)
```
tipo: dose_registrada | medicamento_alterado | medicamento_excluido | dose_atrasada | dose_sem_confirmacao
medicamento_id: <id remoto>          (opcional)
previsto_em: <epoch ms ou ISO>        (opcional)
titulo / mensagem: <texto>            (opcionais)
```
| Evento | Quem recebe | Gatilho necessário |
|---|---|---|
| Paciente registra dose | cuidador | no `POST /api/historys` |
| Cuidador registra dose | paciente | no `POST /api/historys` + token do paciente |
| Medicamento alterado/excluído | cuidador | em `/meds/edit` e `DELETE /meds` |
| Dose atrasada / sem confirmação | cuidador | cron no servidor (o app descarta o alerta se a dose já consta como registrada) |

## 4. O que foi feito no Android
- **Bug corrigido:** `SessionManager.init()` nunca era chamado, então o token Bearer nunca era anexado e o 401 não limpava a sessão. Agora inicia na `MedAlertApplication`.
- **Permissões** (`AccessPolicy`): `canCreateMedication`, `canEditMedication`, `canDeleteMedication`, `canRegisterDose`, `canViewHistory`, `canViewLinkedPatient`. Cuidador registra dose sem poder alterar medicamentos.
- **Room 7→8** (sem apagar nada): `clientEventId`, `origem`, `registradoPorId` + índice `(medicamentoId, previstoEm)` não único. Pendentes antigos ganham `origem="paciente"` e UUID.
- **Identidade da dose:** `medicamento_id` REMOTO + `previsto_em` é adequada como chave natural (os dois aparelhos calculam o mesmo horário a partir do mesmo medicamento remoto), com ressalvas: depende do medicamento não ter sido editado (muda a grade de horários) e só o backend garante unicidade entre aparelhos. O `client_event_id` fica guardado, mas **não é enviado** (a API não o aceita).
- **Dedupe no app:** DAO recusa dose já conhecida por id remoto; antes de enviar, `syncPending` reconcilia o `doseDia` de hoje e não reenvia o que o servidor já tem.
- **`MedicationSyncWorker` (novo):** ordem token FCM → medicamentos pendentes → GET medicamentos → doses pendentes → doseDia. Disparado ao abrir o app, no login/mudança de vínculo, após cadastro offline, por evento FCM, ao voltar a conexão e a cada 15 min.
- **Cuidador registra dose** pelo botão "Registrar" na Home (também disponível ao paciente); origem aparece no Histórico ("pelo cuidador/paciente").
- **FCM:** `MedAlertMessagingService`, `FcmTokenManager`, `RemoteEventHandler`, canal de notificação próprio, service no Manifest. Logout invalida o token e limpa o cache do cuidador (preservando pendentes) via `SessionLifecycle`.
- **Convite:** tela Responsáveis agora chama `requestCuidador`; listar/remover seguem sem endpoint.

## 5. Problemas restantes
- Compilação e testes não executados; testes JVM novos: `AccessPolicyTest`, `TakenEntityFactoryTest`, `RemoteEventTypeTest`. Migration 7→8 e DAO não têm teste instrumentado.
- Paciente com sessão antiga e `userId` de cuidador: nada a fazer; sessão nova resolve.
- Edição/exclusão de medicamento continuam online-only (sem outbox).
- Aceite de convite pelo cuidador ainda usa o repositório mock (`CaregiverRequestRepository`); após aceitar, o cuidador precisa entrar de novo para receber o `pacienteId` (o app sincroniza sozinho quando a sessão muda).
- Duas doses iguais registradas por dois aparelhos em dias anteriores ou com associação ambígua por nome + dosagem só serão deduplicadas pelo backend.
- Remover do repositório (segurança): `usuario_new.md` e `usuarios_teste_medalert.md` com credenciais, se ainda existirem no seu projeto.

## 6. Checklist com dois celulares (A = paciente, B = cuidador)
1. Colocar `google-services.json`, compilar e instalar nos dois.
2. A cria conta de paciente e um medicamento com horário próximo; B cria conta de cuidador.
3. Vincular (convite + aceite conforme o backend); B faz login e vê o medicamento **sem duplicar**.
4. B: verificar que não há botões de cadastrar/editar/excluir.
5. A registra a dose (notificação "Tomar agora"); abrir B (ou aguardar 15 min / push) e conferir "Tomado" e origem "pelo paciente".
6. Repetir com B registrando; em A a dose aparece como registrada e o alarme não deve tocar de novo.
7. Registrar a mesma dose nos dois quase ao mesmo tempo: conferir no backend quantos registros existem.
8. Modo avião em A: registrar dose; religar a rede e conferir envio único.
9. Cadastrar medicamento offline em A; religar a rede e conferir que sobe sozinho e as doses seguem.
10. Logout em B: conferir que o cache do paciente some e que não chegam mais avisos.
11. Com o backend pronto: testar cada evento FCM da seção 3.
