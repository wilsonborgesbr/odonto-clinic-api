# Bokka API

Backend do Bokka, sistema de gestão para clínicas odontológicas.

A API concentra os módulos clínicos, administrativos e financeiros do sistema. O projeto utiliza isolamento de dados por clínica, autenticação JWT e controle de acesso por roles e permissões.

- Produção: https://api.bokka.com.br
- Swagger: https://api.bokka.com.br/swagger-ui/index.html
- Frontend: https://github.com/wilsonborgesbr/odonto-clinic-web
- Aplicação: https://bokka.com.br

## Stack

- Java 17
- Spring Boot 4.0.6
- Spring Security 7.0.5
- Spring Data MongoDB
- MongoDB Atlas
- JWT com Auth0 java-jwt
- Maven
- Bean Validation
- Lombok
- SpringDoc OpenAPI
- JUnit
- Mockito
- Docker
- Nginx
- systemd

## Arquitetura

### Multi-tenant

Cada clínica funciona como um tenant independente.

O isolamento é feito por `clinicaId`, presente nas entidades e utilizado nas consultas ao banco. Clínicas diferentes podem utilizar a mesma aplicação sem compartilhar seus dados.

A autenticação considera três informações:

`código da clínica + email + senha`

Um mesmo email pode existir em clínicas diferentes porque a clínica faz parte do contexto de autenticação.

### Controle de acesso

A autorização utiliza RBAC com 8 roles:

- PROPRIETARIO
- SOCIO
- ADMINISTRADOR
- DENTISTA
- RECEPCIONISTA
- FINANCEIRO
- ESTOQUISTA
- AUXILIAR_CLINICO

Além das roles, o sistema possui 14 permissões granulares que podem ser configuradas por usuário.

As permissões são incluídas no JWT e usadas pelo frontend para controlar rotas, menus e ações disponíveis. A autorização dos endpoints permanece no backend.

## Escopo da API

Atualmente o backend possui:

- 14 controllers
- 84 endpoints REST
- 16 entidades de domínio
- 18 enums

Os principais módulos são:

- autenticação e usuários
- pacientes
- dentistas
- funcionários
- agendamentos
- procedimentos
- odontograma
- anamnese
- documentos clínicos
- convênios
- estoque
- contas a pagar
- contas a receber
- permissões e controle de acesso

## Regras de negócio

Entre as regras implementadas estão:

- soft delete para entidades que precisam manter histórico
- validação de CPF para pacientes e funcionários
- validação de CRO para dentistas
- verificação de conflito de horário em agendamentos
- validação de entidades relacionadas antes da persistência
- controle de estoque mínimo
- pagamentos parciais em contas a receber
- atualização automática de status financeiro
- proteção da conta proprietária contra inativação ou rebaixamento de role

A validação de conflito de horário utiliza uma query própria para tratar os diferentes cenários de sobreposição entre atendimentos.

## Banco de dados

### Desenvolvimento

O ambiente local utiliza MongoDB em Docker. O MongoDB Compass pode ser usado para visualizar os dados durante o desenvolvimento.

Banco utilizado pelo profile local:

```text
mongodb://localhost:27017/sistema_clinica
```

### Produção

Em produção, o backend executado na VPS utiliza MongoDB Atlas.

```text
bokka.com.br
     |
     v
api.bokka.com.br
     |
     v
MongoDB Atlas
```

## Executando localmente

Clone o projeto:

```bash
git clone https://github.com/wilsonborgesbr/odonto-clinic-api.git
cd odonto-clinic-api
```

Crie o volume utilizado pelo MongoDB local:

```bash
docker volume create bokka-mongo-data
```

Suba o banco:

```bash
docker compose up -d
```

### Linux e macOS

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Windows PowerShell

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

A API ficará disponível em:

```text
http://localhost:8080
```

O profile local utiliza `application-local.properties` e aponta para o MongoDB local.

Para popular usuários de desenvolvimento em um banco novo, também é possível utilizar:

```text
-Dbokka.seed.dev-users=true
```

## Testes

Linux e macOS:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

A suíte utiliza JUnit e Mockito para validar regras de negócio dos services.

## Documentação da API

Com a aplicação local em execução:

http://localhost:8080/swagger-ui/index.html

Em produção:

https://api.bokka.com.br/swagger-ui/index.html

O Swagger mantém a relação dos endpoints, parâmetros e modelos utilizados pela API.

## Estrutura

```text
src/main/java/com/example/demo/
├── config/
├── controller/
├── dto/
├── enums/
├── model/
├── repository/
└── service/
```

- `config` concentra segurança, JWT e CORS.
- `controller` expõe os endpoints REST.
- `dto` contém os objetos utilizados na entrada e saída da API.
- `model` contém os documentos persistidos no MongoDB.
- `repository` concentra os repositórios do Spring Data.
- `service` contém as principais regras de negócio.

## Produção

O backend está hospedado em uma VPS Linux com:

- Ubuntu 24.04
- systemd
- Nginx
- SSL via Let's Encrypt
- MongoDB Atlas

O Nginx recebe as requisições de `api.bokka.com.br` e encaminha para a aplicação Spring Boot.

O deploy do backend é controlado separadamente do frontend.

## Autor

Wilson Borges

Estudante de Análise e Desenvolvimento de Sistemas.

- GitHub: https://github.com/wilsonborgesbr
- LinkedIn: https://linkedin.com/in/wilsonborgeslima
