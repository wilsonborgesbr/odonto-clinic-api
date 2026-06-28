# Odonto Clinic API

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-47A248?logo=mongodb&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)

API REST para gerenciamento completo de clínica odontológica. Cobre desde o cadastro de pacientes e dentistas até controle financeiro, estoque e documentos clínicos.

## Tecnologias

- **Java 17** com **Spring Boot 4.0.6**
- **Spring Data MongoDB** — persistência NoSQL
- **Spring Security + JWT** (Auth0 java-jwt) — autenticação stateless
- **Bean Validation** — validação de entrada
- **Lombok** — redução de boilerplate
- **Maven** — build e dependências

## Como executar

```bash
# 1. Clone o repositório
git clone https://github.com/wilsonborgesbr/odonto-clinic-api.git
cd odonto-clinic-api/demo

# 2. Configure a connection string do MongoDB em:
#    src/main/resources/application.properties

# 3. Execute
./mvnw spring-boot:run
```

A API sobe na porta **8080** por padrão. Todos os endpoints (exceto `/auth/*`) exigem token JWT no header `Authorization: Bearer <token>`.

## Endpoints

### Autenticação

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/auth/login` | Autentica e retorna o token JWT |
| `POST` | `/auth/register` | Registra novo usuário |

### Pacientes

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/pacientes` | Cadastrar paciente |
| `GET` | `/api/pacientes` | Listar pacientes ativos |
| `GET` | `/api/pacientes/{id}` | Buscar por ID |
| `PUT` | `/api/pacientes/{id}` | Atualizar cadastro |
| `DELETE` | `/api/pacientes/{id}` | Inativar (soft delete) |

### Dentistas

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/dentistas` | Cadastrar dentista |
| `GET` | `/api/dentistas` | Listar dentistas ativos |
| `GET` | `/api/dentistas/{id}` | Buscar por ID |
| `PUT` | `/api/dentistas/{id}` | Atualizar cadastro |
| `DELETE` | `/api/dentistas/{id}` | Inativar (soft delete) |

### Funcionários

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/funcionarios` | Cadastrar funcionário |
| `GET` | `/api/funcionarios` | Listar funcionários ativos |
| `GET` | `/api/funcionarios/{id}` | Buscar por ID |
| `PUT` | `/api/funcionarios/{id}` | Atualizar cadastro |
| `DELETE` | `/api/funcionarios/{id}` | Inativar (soft delete) |

### Agendamentos

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/agendamentos` | Criar agendamento |
| `GET` | `/api/agendamentos` | Listar todos |
| `GET` | `/api/agendamentos/{id}` | Buscar por ID |
| `GET` | `/api/agendamentos/paciente/{pacienteId}` | Filtrar por paciente |
| `GET` | `/api/agendamentos/dentista/{dentistaId}` | Filtrar por dentista |
| `GET` | `/api/agendamentos/status/{status}` | Filtrar por status |
| `PUT` | `/api/agendamentos/{id}` | Atualizar agendamento |
| `DELETE` | `/api/agendamentos/{id}` | Excluir agendamento |

### Procedimentos

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/procedimentos` | Criar procedimento |
| `GET` | `/api/procedimentos/{id}` | Buscar por ID |
| `GET` | `/api/procedimentos/paciente/{pacienteId}` | Listar por paciente |
| `GET` | `/api/procedimentos/paciente/{pacienteId}/status/{status}` | Filtrar por paciente e status |
| `PUT` | `/api/procedimentos/{id}` | Atualizar procedimento |
| `DELETE` | `/api/procedimentos/{id}` | Excluir procedimento |

### Anamneses

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/anamneses` | Criar anamnese |
| `GET` | `/api/anamneses/{id}` | Buscar por ID |
| `GET` | `/api/anamneses/paciente/{pacienteId}` | Histórico do paciente |
| `GET` | `/api/anamneses/paciente/{pacienteId}/recente` | Anamnese mais recente |

### Odontogramas

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/odontogramas` | Criar odontograma |
| `GET` | `/api/odontogramas/{id}` | Buscar por ID |
| `GET` | `/api/odontogramas/paciente/{pacienteId}` | Histórico do paciente |
| `GET` | `/api/odontogramas/paciente/{pacienteId}/recente` | Odontograma mais recente |

### Contas a Receber

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/contas-receber` | Criar conta |
| `GET` | `/api/contas-receber` | Listar todas |
| `GET` | `/api/contas-receber/{id}` | Buscar por ID |
| `GET` | `/api/contas-receber/paciente/{pacienteId}` | Filtrar por paciente |
| `GET` | `/api/contas-receber/status/{status}` | Filtrar por status |
| `PUT` | `/api/contas-receber/{id}` | Atualizar conta |
| `PATCH` | `/api/contas-receber/{id}/pagamento?valor=X` | Registrar pagamento |
| `DELETE` | `/api/contas-receber/{id}` | Excluir conta |

### Contas a Pagar

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/contas-pagar` | Criar conta |
| `GET` | `/api/contas-pagar` | Listar todas |
| `GET` | `/api/contas-pagar/{id}` | Buscar por ID |
| `GET` | `/api/contas-pagar/status/{status}` | Filtrar por status |
| `PUT` | `/api/contas-pagar/{id}` | Atualizar conta |
| `DELETE` | `/api/contas-pagar/{id}` | Excluir conta |

### Convênios

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/convenios` | Cadastrar convênio |
| `GET` | `/api/convenios` | Listar ativos |
| `GET` | `/api/convenios/{id}` | Buscar por ID |
| `PUT` | `/api/convenios/{id}` | Atualizar convênio |
| `DELETE` | `/api/convenios/{id}` | Inativar (soft delete) |

### Estoque

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/estoque` | Cadastrar item |
| `GET` | `/api/estoque` | Listar todos os itens ativos |
| `GET` | `/api/estoque/{id}` | Buscar por ID |
| `GET` | `/api/estoque/abaixo-minimo` | Itens com estoque baixo |
| `GET` | `/api/estoque/categoria/{categoria}` | Filtrar por categoria |
| `PUT` | `/api/estoque/{id}` | Atualizar item |
| `DELETE` | `/api/estoque/{id}` | Inativar (soft delete) |

### Documentos

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/documentos` | Criar documento |
| `GET` | `/api/documentos/{id}` | Buscar por ID |
| `GET` | `/api/documentos/paciente/{pacienteId}` | Listar por paciente |
| `DELETE` | `/api/documentos/{id}` | Excluir documento |

## Estrutura de pastas

```
demo/src/main/java/com/example/demo/
├── config/             # SecurityConfig, SecurityFilter (JWT)
├── controller/         # REST controllers
├── dto/                # Data Transfer Objects (auth)
├── enums/              # Enumerações de domínio
├── model/              # Entidades / documentos MongoDB
├── repository/         # Interfaces Spring Data MongoDB
└── service/            # Regras de negócio
```

## Regras de negócio relevantes

- **Soft delete** em Pacientes, Dentistas, Funcionários, Convênios e Estoque — o registro é inativado, não excluído.
- **Validação de duplicidade** no CPF (Paciente, Funcionário) e CRO (Dentista).
- **Conflito de horário** no agendamento — a API impede que o mesmo dentista tenha dois atendimentos sobrepostos.
- **Validação de existência** — ao criar agendamento, procedimento, anamnese ou odontograma, o paciente e/ou dentista referenciado precisa existir e estar ativo.

## Autor

**Wilson Borges** — Estudante de Análise e Desenvolvimento de Sistemas

- GitHub: [github.com/wilsonborgesbr](https://github.com/wilsonborgesbr)
- LinkedIn: [linkedin.com/in/wilsonborgeslima](https://www.linkedin.com/in/wilsonborgeslima/)
