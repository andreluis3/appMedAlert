# Arquitetura do MedAlert — MVVM

O MedAlert segue o padrão arquitetural **MVVM (Model–View–ViewModel)**. Os componentes trabalham juntos para enviar dados à API, tratar as respostas e manter informações disponíveis no aplicativo.

## 1. DTO (Data Transfer Object)

**O que é:** o objeto que transporta os dados entre o aplicativo e a API.

**Função:** define a estrutura dos dados enviados ao servidor. Por exemplo, `MedicamentoData` especifica os campos esperados pela API, como `userId`, evitando divergências de nomes, como enviar `id_usuario` quando o servidor espera `userId`.

## 2. API (Retrofit)

**O que é:** o componente responsável pela comunicação HTTP com o servidor.

**Função:** o Retrofit serializa o DTO em JSON e envia a requisição para o endpoint da API na Vercel:

`https://dosy-xi.vercel.app/api/meds`

Depois, recebe a resposta do servidor — por exemplo, o identificador do medicamento criado — e disponibiliza o resultado para as camadas do aplicativo.

## 3. ViewModel

**O que é:** o intermediário que gerencia o estado e a lógica da tela.

**Função:** quando o usuário toca em **Salvar** na tela `CadastroMedScreen`, a View comunica a ação ao ViewModel. Ele coordena o fluxo sem concentrar toda a lógica dentro da tela.

O ViewModel pode:

1. Manter a lógica de apresentação fora da View.
2. Chamar o Repository para salvar ou consultar os dados.
3. Gerenciar estados de carregamento (*Loading*), sucesso e mensagens de erro.
4. Coordenar a persistência local por meio do Repository e do Room, quando essa estratégia estiver implementada.

## Fluxo simplificado

1. O usuário preenche os dados em `CadastroMedScreen`.
2. A View encaminha a ação ao ViewModel.
3. O ViewModel chama o Repository.
4. O Repository utiliza a API/Retrofit para enviar os dados ao servidor.
5. A API retorna uma resposta, que é tratada pelas camadas do aplicativo.
6. O aplicativo atualiza o estado da tela e, quando configurado, persiste os dados localmente com Room.

> **Observação:** o Room permite persistência local, mas o funcionamento sem internet depende de uma estratégia de armazenamento e sincronização offline implementada no aplicativo.
