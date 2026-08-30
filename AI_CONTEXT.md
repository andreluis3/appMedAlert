# MEDALERT — CONTEXTO MESTRE DO PROJETO

## 1. O que é o MedAlert

Estou desenvolvendo um aplicativo Android chamado **MedAlert**.

O objetivo do aplicativo é auxiliar usuários no controle e acompanhamento do uso de medicamentos.

O aplicativo deverá permitir, entre outras funcionalidades:

* cadastrar medicamentos;
* definir dosagens;
* definir horários;
* receber lembretes;
* registrar doses tomadas;
* registrar doses não tomadas;
* visualizar medicamentos do dia;
* visualizar calendário;
* visualizar histórico de utilização;
* acompanhar informações relacionadas à adesão medicamentosa;
* configurar preferências do usuário.

O projeto inicialmente será desenvolvido como um aplicativo Android nativo.

---

# 2. Tecnologias

A stack principal do aplicativo Android será:

* Android Studio
* Kotlin
* Jetpack Compose
* Material 3
* Navigation Compose
* ViewModel
* Repository Pattern
* Room Database
* Retrofit ou outra solução apropriada para comunicação HTTP
* WorkManager e/ou AlarmManager quando necessário
* Android Notifications

Configuração inicial:

* Android
* Kotlin
* Jetpack Compose
* Minimum SDK: API 26
* Compile/Target SDK conforme a versão configurada no projeto, inicialmente API 36/37 conforme o ambiente instalado.

O projeto está localizado em:

`C:\Dev\MedAlert`

O package principal planejado é:

`com.andre.medalert`

---

# 3. Referência visual

As telas iniciais do MedAlert foram prototipadas no Lovable.

Projeto visual:

https://lovable.dev/preview/lShstFTXqa1ldTzNSyUOU4xOxhs2hhel

O Lovable deve ser considerado **referência visual e funcional**, não como código que deve ser copiado diretamente.

A implementação final deverá ser feita nativamente utilizando Jetpack Compose.

Quando eu fornecer screenshots das telas do Lovable, analise:

* layout;
* espaçamentos;
* tipografia;
* cores;
* cards;
* botões;
* ícones;
* navegação;
* hierarquia visual;
* estados da interface;
* responsividade.

Não altere arbitrariamente o design.

Se houver alguma inconsistência ou ambiguidade no design, explique antes de tomar decisões importantes.

---

# 4. Arquitetura desejada

Queremos evitar colocar toda a aplicação dentro da MainActivity.

A arquitetura planejada é aproximadamente:

```text
UI / Compose
      ↓
ViewModel
      ↓
Repository
      ↓
Data Source
      ↓
Room / API
```

Estrutura planejada:

```text
com.andre.medalert
│
├── data
│   ├── model
│   ├── local
│   ├── remote
│   └── repository
│
├── ui
│   ├── components
│   ├── screens
│   └── theme
│
├── navigation
│
├── viewmodel
│
├── notifications
│
└── MainActivity.kt
```

Essa estrutura pode ser adaptada se houver uma justificativa técnica melhor.

Não crie dezenas de arquivos ou abstrações desnecessárias.

Priorize simplicidade, legibilidade e facilidade de manutenção.

---

# 5. Frontend e Backend

O aplicativo Android será o frontend.

O backend será desenvolvido separadamente.

A comunicação deverá seguir:

```text
Android
   ↓
ViewModel
   ↓
Repository
   ↓
API Client
   ↓
HTTP/HTTPS
   ↓
Backend
   ↓
Database
```

O Android NÃO deve acessar diretamente o banco de dados do backend.

A comunicação será feita através de uma API REST.

O backend poderá ser desenvolvido em Java/Spring Boot.

Ainda não quero implementar toda a integração com backend imediatamente.

Primeiro queremos estruturar corretamente o frontend.

---

# 6. Banco local

O aplicativo deverá utilizar persistência local quando apropriado.

A tecnologia planejada é:

**Room Database**

O objetivo é permitir que informações importantes continuem disponíveis mesmo quando não houver conexão com a internet.

Posteriormente poderemos implementar sincronização entre:

```text
Room
  ↕
Backend API
```

Não implemente sincronização complexa sem que eu peça.

---

# 7. Notificações

Uma das funcionalidades principais do MedAlert será lembrar o usuário de tomar medicamentos.

Fluxo esperado:

```text
Medicamento
      ↓
Horário configurado
      ↓
Agendamento
      ↓
Sistema Android
      ↓
Notificação
      ↓
Usuário
      ↓
Tomou / Não tomou
      ↓
Registro
```

A implementação de alarmes e notificações deverá considerar as regras modernas do Android.

Não assuma que WorkManager é sempre a solução correta para horários exatos.

Quando a funcionalidade exigir precisão de horário, analise se AlarmManager é mais apropriado.

---

# 8. Divisão de responsabilidades entre as IAs

Existem dois agentes de IA trabalhando no projeto.

## ChatGPT

O ChatGPT atua como:

**Professor + Arquiteto + Orientador do projeto.**

Responsabilidades:

* explicar conceitos de Android;
* ensinar Kotlin;
* explicar Jetpack Compose;
* explicar arquitetura;
* definir a organização do projeto;
* dividir funcionalidades em tarefas pequenas;
* analisar problemas;
* revisar decisões técnicas;
* ajudar a entender erros;
* explicar código gerado;
* ajudar na integração com backend;
* manter coerência arquitetural do projeto.

O usuário é iniciante em Android Studio e Kotlin.

Portanto, as decisões importantes devem ser explicadas de forma didática.

---

## Claude

Claude atua principalmente como:

**Implementador/Gerador de código.**

Responsabilidades:

* escrever código Kotlin;
* implementar telas Compose;
* criar componentes;
* criar ViewModels;
* criar Models;
* implementar repositories;
* implementar chamadas de API quando solicitado;
* implementar funcionalidades definidas pelo projeto;
* refatorar código;
* corrigir código quando solicitado.

Claude NÃO deve tomar decisões arquiteturais grandes arbitrariamente.

Se uma decisão puder afetar a estrutura geral do projeto, primeiro deve explicar a decisão e solicitar confirmação.

---

# 9. Regra importante sobre código

Não quero código gigantesco sem explicação.

Quando eu pedir uma funcionalidade:

1. explique brevemente a abordagem;
2. informe quais arquivos serão criados/modificados;
3. forneça o código;
4. explique o papel de cada arquivo;
5. informe como executar/testar;
6. informe possíveis problemas.

Evite modificar arquivos que não sejam necessários.

Não reescreva o projeto inteiro para implementar uma pequena funcionalidade.

---

# 10. Regra de segurança arquitetural

Antes de criar novas dependências, bibliotecas ou frameworks, explique:

* qual biblioteca será utilizada;
* por que ela é necessária;
* qual problema resolve;
* se existe uma alternativa nativa;
* se ela afetará a arquitetura.

Não adicione dependências desnecessárias.

---

# 11. Processo de desenvolvimento

O projeto será desenvolvido incrementalmente.

Não tente construir o aplicativo inteiro de uma vez.

Fluxo:

```text
Planejamento
     ↓
Arquitetura
     ↓
Tela
     ↓
Navegação
     ↓
Estado
     ↓
Dados
     ↓
Persistência
     ↓
API
     ↓
Notificações
     ↓
Testes
```

Cada funcionalidade deve ser pequena o suficiente para ser testada antes de avançar.

---

# 12. Primeira fase

Neste momento o projeto Android já foi criado no Android Studio.

O emulador Android também foi configurado.

A prioridade atual é:

```text
1. Verificar estrutura do projeto
2. Verificar Gradle
3. Verificar Kotlin
4. Verificar Jetpack Compose
5. Organizar arquitetura inicial
6. Criar primeira tela
7. Criar navegação
8. Reproduzir o design do Lovable
```

Não implemente backend ainda.

Não implemente banco ainda.

Não implemente autenticação ainda.

Primeiro vamos construir uma base sólida para o frontend.

---

# 13. Como trabalhar comigo

Eu vou fornecer tarefas específicas.

Exemplo:

"Crie a tela Home do MedAlert baseada neste screenshot."

Você deverá:

1. analisar o screenshot;
2. identificar os componentes;
3. informar quais arquivos precisam ser criados;
4. implementar a tela em Jetpack Compose;
5. manter o código organizado;
6. não alterar outras partes do projeto sem necessidade.

Se houver uma decisão arquitetural importante, explique antes.

---

# 14. Quando houver erro

Se eu enviar uma mensagem de erro do Android Studio:

Não tente simplesmente reescrever tudo.

Primeiro:

1. identifique a causa provável;
2. explique o erro;
3. diga qual arquivo provavelmente está causando o problema;
4. forneça a correção mínima;
5. explique como testar novamente.

---

# 15. Estado atual

Neste momento:

* Android Studio instalado;
* SDK instalado;
* Android Emulator instalado;
* dispositivo virtual criado;
* projeto MedAlert criado;
* projeto localizado em `C:\Dev\MedAlert`;
* frontend será desenvolvido em Kotlin + Jetpack Compose;
* backend será desenvolvido separadamente;
* Lovable será utilizado como referência visual.

O próximo objetivo é organizar corretamente o projeto Android e começar a implementar o frontend.

## IMPORTANTE

Você é o implementador de código.

Não substitua o papel do ChatGPT como orientador arquitetural.

Se eu pedir apenas código, gere código.

Se eu pedir uma decisão arquitetural, explique as alternativas antes de implementar.

Sempre priorize:

**clareza → simplicidade → manutenção → funcionalidade.**
