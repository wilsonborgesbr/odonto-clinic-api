# Bokka API — Backend

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-47A248?logo=mongodb&logoColor=white)](https://www.mongodb.com/atlas)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org)
[![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203-85EA2D?logo=swagger&logoColor=black)](https://swagger.io)
[![Deploy](https://img.shields.io/badge/Deploy-VPS%20Linux-FCC624?logo=linux&logoColor=black)](https://api.bokka.com.br)

API REST multi-tenant para gestão completa de clínicas odontológicas. Cobre desde cadastro de pacientes e dentistas até controle financeiro, estoque, odontograma, anamnese e documentos clínicos, com isolamento total de dados por clínica.

🔗 **Produção:** [api.bokka.com.br](https://api.bokka.com.br)
🔗 **Swagger UI:** [api.bokka.com.br/swagger-ui/index.html](https://api.bokka.com.br/swagger-ui/index.html)
🔗 **Frontend:** [github.com/wilsonborgesbr/odonto-clinic-web](https://github.com/wilsonborgesbr/odonto-clinic-web) | [bokka.com.br](https://bokka.com.br)

---

## Tecnologias

- **Java 17** com **Spring Boot 4.0.6**
- **Spring Security 6** + **JWT** (Auth0 java-jwt, HMAC256, BCrypt) — autenticação stateless
- **Spring Data MongoDB** — persistência NoSQL
- **MongoDB Atlas** (cloud) — banco de dados em produção
- **Bean Validation** — validação de entrada
- **Lombok** — redução de boilerplate
- **Maven** — build e dependências
- **SpringDoc OpenAPI** — documentação interativa via Swagger UI

## Arquitetura

### Multi-Tenant

Isolamento de dados por `clinicaId` com índice composto. Cada clínica é um tenant independente:
- **Login triplo:** código da clínica + email + senha
- Um mesmo email pode existir em clínicas diferentes sem conflito
- Todas as queries filtram automaticamente pelo tenant do usuário autenticado

### RBAC (Role-Based Access Control)

- **8 roles hierárquicos:** PROPRIETARIO, SOCIO, ADMINISTRADOR, DENTISTA, RECEPCIONISTA, FINANCEIRO, ESTOQUISTA, AUXILIAR_CLINICO
- **14 permissões granulares** customizáveis por usuário individual
- Permissões propagadas via **claims JWT** até o frontend (sidebar e botões condicionais)
- Proteção do proprietário: não pode ser inativado nem ter role rebaixado

### Escopo

- **14 controllers**, **84 endpoints REST**, **16 entidades de domínio**, **18 enums**
- **Módulos:** Autenticação, Pacientes, Dentistas, Funcionários, Agendamentos, Procedimentos, Odontograma, Anamnese, Documentos, Convênios, Estoque, Financeiro (contas a pagar/receber com parcelas e pagamento parcial), Usuários/Permissões

## Regras de negócio

- **Soft delete** em Pacientes, Dentistas, Funcionários, Convênios e Estoque
- **Validação de duplicidade** no CPF (Paciente, Funcionário) e CRO (Dentista)
- **Conflito de horário** — @Query customizada cobrindo os 4 cenários reais de sobreposição (não usa o método derivado do Spring Data, que cobria apenas 2)
- **Validação de existência** — entidades referenciadas precisam existir e estar ativas
- **Alerta de estoque mínimo** — endpoint dedicado para itens abaixo do limite
- **Pagamento parcial** em Contas a Receber com atualização automática de status
- **Proteção do proprietário** — não pode ser inativado nem rebaixado

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

## Documentação interativa (Swagger)

Disponível em produção:

🔗 [api.bokka.com.br/swagger-ui/index.html](https://api.bokka.com.br/swagger-ui/index.html)

### Como testar

1. Acesse o Swagger UI no link acima
2. Registre um usuário em `POST /auth/register`
3. Faça login em `POST /auth/login` e copie o token retornado
4. Clique em **Authorize** no topo da página e cole o token
5. Teste qualquer endpoint diretamente pela interface

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
├── config/             # SecurityConfig, SecurityFilter (JWT), CORS
├── controller/         # 14 REST controllers
├── dto/                # Data Transfer Objects (auth, requests, responses)
├── enums/              # 18 enumerações de domínio
├── model/              # 16 entidades / documentos MongoDB
├── repository/         # Interfaces Spring Data MongoDB
└── service/            # Regras de negócio
```

## Infraestrutura

- **VPS Linux** (Ubuntu 24.04) com **systemd** para gerenciamento do processo
- **Nginx** como reverse proxy + **Let's Encrypt SSL**
- **MongoDB Atlas** (cloud) como banco de dados
- **Domínio:** [api.bokka.com.br](https://api.bokka.com.br)
- CORS restrito aos domínios de produção
- Sem CI/CD automatizado; deploy manual via SFTP + restart systemd

## Autor

**Wilson Borges** — Estudante de Análise e Desenvolvimento de Sistemas

- GitHub: [github.com/wilsonborgesbr](https://github.com/wilsonborgesbr)
- LinkedIn: [linkedin.com/in/wilsonborgeslima](https://www.linkedin.com/in/wilsonborgeslima/)
