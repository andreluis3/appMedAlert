# Usuários de Teste — MedAlert

> **Atenção:** este arquivo contém credenciais de teste. Não use senhas reais nem versione este arquivo em um repositório público.

## Lista de usuários disponíveis

| Perfil | E-mail | Senha | ID (UID) |
|---|---|---|---:|
| Teste Postman | `teste.postman.01@email.com` | `SenhaUnica@2026` | 10 |
| Teste App André | `andretestandoapidenovo@email.com` | `teste44` | 11 |

## Como testar o login

1. Abra o MedAlert no simulador **Pixel 8**.
2. Na tela de Login, insira um dos e-mails da tabela.
3. Digite a senha correspondente.
4. Clique em **Entrar**.

## O que verificar após o login

- **Sucesso:** o aplicativo deve salvar o `userId` (`10` ou `11`) no DataStore e navegar automaticamente para a tela Home.
- **Falha:** abra o Logcat e filtre por `OkHttp`. Verifique o código HTTP retornado pelo servidor:
  - `200`: requisição concluída com sucesso.
  - `401`: falha de autenticação ou credenciais inválidas.
  - `500`: erro interno no servidor.

## Criar um usuário novo para teste

Para testar o fluxo completo, do cadastro até a Home:

1. Abra a tela de Cadastro do aplicativo.
2. Cadastre um e-mail novo, por exemplo, `teste.outubro@email.com`.
3. Conclua o cadastro e verifique o login e a navegação para a Home.

O ID gerado pode ser o próximo da sequência (por exemplo, `12`), mas isso depende de como o banco de dados gera os identificadores. Confirme o UID retornado pela API ou salvo no banco.
