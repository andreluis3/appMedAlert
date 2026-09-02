# 💊 MedAlert

**Sistema Inteligente de Apoio ao Uso de Medicamentos**

O **MedAlert** é um aplicativo mobile desenvolvido com o objetivo de auxiliar usuários no **controle, organização e acompanhamento do uso de medicamentos**.

O projeto busca facilitar a rotina de pessoas que precisam utilizar medicamentos em horários específicos, oferecendo recursos para cadastro de medicamentos, definição de horários, lembretes, acompanhamento das doses e visualização do histórico de utilização.

> 🚧 **Projeto em desenvolvimento**
> Este repositório contém o desenvolvimento atual do MedAlert. Novas funcionalidades, melhorias e integrações serão adicionadas ao longo do projeto.

---

## 🎯 Objetivo

O objetivo principal do MedAlert é desenvolver uma ferramenta que ajude o usuário a manter uma rotina mais organizada em relação ao uso de seus medicamentos.

A aplicação pretende permitir que o usuário:

* 💊 Cadastre seus medicamentos;
* ⏰ Defina horários para cada medicamento;
* 🔔 Receba lembretes para tomar seus medicamentos;
* ✅ Registre medicamentos que foram tomados;
* 📅 Consulte medicamentos programados no calendário;
* 📊 Acompanhe sua adesão ao tratamento;
* 📖 Consulte o histórico de medicamentos;
* 👨‍👩‍👧 Cadastre responsáveis ou acompanhantes;
* ⚙️ Configure preferências do aplicativo.

---

## 📱 Funcionalidades

O projeto está sendo desenvolvido de forma incremental.

Entre as funcionalidades planejadas e em desenvolvimento estão:

### 🏠 Início

A tela inicial apresenta um resumo da rotina de medicamentos do usuário.

Entre as informações apresentadas estão:

* Medicamentos programados para o dia;
* Horários dos medicamentos;
* Status das doses;
* Porcentagem de adesão ao tratamento;
* Quantidade de responsáveis acompanhando o usuário.

### 💊 Medicamentos

Tela destinada ao gerenciamento dos medicamentos cadastrados.

Funcionalidades previstas:

* Visualização dos medicamentos;
* Cadastro de medicamento;
* Edição de medicamento;
* Exclusão de medicamento;
* Definição de dosagem;
* Definição de horários;
* Definição de frequência;
* Data de início e término;
* Observações.

### ⏰ Horários personalizados

Além dos horários pré-definidos, o usuário poderá escolher um **horário personalizado**, utilizando um seletor semelhante ao de um despertador de celular.

Isso permite, por exemplo, configurar:

* 07:15
* 09:40
* 13:25
* 18:35
* 22:10

Também será possível configurar múltiplos horários para um mesmo medicamento.

### 📅 Calendário

O calendário permitirá visualizar os medicamentos programados de acordo com as datas.

Futuramente, essa funcionalidade será integrada aos dados armazenados no banco de dados.

### 📖 Histórico

O histórico será utilizado para acompanhar os registros de utilização dos medicamentos.

A aplicação deverá permitir consultar informações como:

* Medicamentos tomados;
* Medicamentos não tomados;
* Horário programado;
* Horário em que o medicamento foi tomado;
* Registros de hoje;
* Registros da semana;
* Registros do mês;
* Histórico por período.

### 📊 Adesão ao tratamento

O MedAlert também contará com um indicador de adesão ao tratamento.

A porcentagem será calculada futuramente a partir dos registros reais de medicamentos tomados e não tomados.

Exemplo:

**92%**

Adesão ao tratamento

### 👨‍👩‍👧 Responsáveis e acompanhantes

O usuário poderá cadastrar pessoas responsáveis pelo acompanhamento de sua rotina de medicamentos.

Essa funcionalidade poderá permitir futuramente:

* Adicionar responsável;
* Editar responsável;
* Remover responsável;
* Acompanhar informações relacionadas à rotina de medicamentos.

### ⚙️ Configurações

Área destinada às configurações e preferências do aplicativo.

---

# 🏗️ Desenvolvimento atual

Neste momento, o projeto está concentrado principalmente no desenvolvimento da **interface e experiência do usuário**.

A aplicação já possui a estrutura inicial das principais telas e navegação entre elas.

Atualmente estamos utilizando **dados mockados** para desenvolver e testar a interface antes da implementação completa do backend.

A estratégia de desenvolvimento é:

```text
Interface
   ↓
Navegação
   ↓
Componentes
   ↓
Modelos de dados
   ↓
Backend
   ↓
Banco de dados
   ↓
Notificações e funcionalidades reais
```

---

# 🧱 Arquitetura planejada

A aplicação está sendo desenvolvida visando uma arquitetura que permita a evolução do projeto sem necessidade de reescrever as telas existentes.

A estrutura planejada futuramente será semelhante a:

```text
UI / Jetpack Compose
        ↓
    ViewModel
        ↓
   Repository
        ↓
      DAO
        ↓
      Room
        ↓
     SQLite
```

Essa estrutura permitirá separar a interface da lógica de negócio e da persistência dos dados.

---

# 🗃️ Dados e Backend

O backend ainda está em desenvolvimento.

Entre as estruturas de dados que deverão ser implementadas futuramente estão:

```text
User
 ├── id
 ├── name
 └── ...

Medication
 ├── id
 ├── name
 ├── dosage
 ├── schedules
 ├── frequency
 ├── startDate
 ├── endDate
 └── notes

MedicationHistory
 ├── id
 ├── medicationId
 ├── scheduledTime
 ├── takenAt
 ├── status
 └── date

Responsible
 ├── id
 ├── name
 ├── relationship
 └── ...
```

A estrutura definitiva será definida durante a implementação do backend.

---

# 🛠️ Tecnologias

O projeto está sendo desenvolvido utilizando tecnologias voltadas para desenvolvimento Android:

* **Kotlin**
* **Android Studio**
* **Jetpack Compose**
* **Material 3**
* **Git**
* **GitHub**

Tecnologias adicionais, como **Room**, serão incorporadas posteriormente durante a implementação da persistência dos dados.

---

# 👨‍💻 Equipe

O MedAlert está sendo desenvolvido por **5 estudantes de Engenharia da Computação**.

O projeto faz parte do processo de aprendizado e desenvolvimento acadêmico da equipe, envolvendo conceitos de:

* Desenvolvimento mobile;
* Engenharia de software;
* Programação orientada a objetos;
* Arquitetura de software;
* Banco de dados;
* Interface e experiência do usuário;
* Desenvolvimento colaborativo;
* Controle de versão.

---

# 📚 Contexto acadêmico

O desenvolvimento do MedAlert proporciona à equipe a oportunidade de aplicar conhecimentos adquiridos ao longo do curso de **Engenharia da Computação** em um projeto prático.

Além do desenvolvimento da aplicação, o projeto envolve planejamento, documentação, organização do código, controle de versões e evolução incremental do sistema.

---

---

# 📌 Repositório

Este repositório contém o desenvolvimento do **MedAlert**, desde sua estrutura inicial até a implementação progressiva de suas funcionalidades.

O projeto será atualizado conforme novas funcionalidades forem desenvolvidas pela equipe.

```text
MedAlert
Sistema Inteligente de Apoio ao Uso de Medicamentos
```

---

## 📄 Licença

Este projeto está sendo desenvolvido para fins **acadêmicos e educacionais**.
