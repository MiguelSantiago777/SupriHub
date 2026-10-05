# SupriHub

Sistema de credenciamento de fornecedores e editais do **Contrata + NATA — Instituto NATA**
(Mesquita/RJ). Substitui a Planilha de Gestão e abre o processo ao público, com
prioridade a MEIs, pequenos negócios, cooperativas, autônomos e negócios liderados por
mulheres.

> Estado atual: **Fase 0** (esqueleto, autenticação, catálogo, parâmetros, calendário,
> auditoria, mensageria). As Fases 1 (fornecedores) e 2 (editais e julgamento) vêm a seguir.

## Rodar tudo com Docker

Pré-requisito: Docker Desktop (ou Docker Engine + Compose v2).

```bash
cp .env.example .env        # opcional em dev; os padrões já funcionam
docker compose up --build
```

| Serviço | Endereço |
|---|---|
| Aplicação | http://localhost:4200 |
| API / Swagger UI | http://localhost:4200/swagger-ui (ou :8080/swagger-ui) |
| MailHog (e-mails de dev) | http://localhost:8025 |
| RabbitMQ (painel) | http://localhost:15672 (suprihub / suprihub) |
| PostgreSQL | localhost:5432 (suprihub / suprihub) |

### Usuários de desenvolvimento

No perfil `dev` são criados automaticamente:

| Perfil | E-mail | Senha |
|---|---|---|
| ADMIN | `admin@suprihub.local` | `Admin@12345` |
| Demais perfis | `supplier@`, `commission@`, `demand-manager@`, `finance@`, `internal-control@`, `board@`, `admin@` + `suprihub.local` | `Senha@123` |

Para testar o autocadastro: em http://localhost:4200, clique em "Quero me credenciar" e
abra o link de confirmação no MailHog.

## Desenvolvimento local (sem Docker para app)

Suba só a infraestrutura:

```bash
docker compose up -d postgres rabbitmq mailhog
```

**Backend** (Java 21):

```bash
cd backend
./mvnw spring-boot:run          # perfil dev; API em http://localhost:8080
./mvnw verify                   # testes unitários + integração (exige Docker para Testcontainers)
```

> Windows com outro JDK no PATH: `JAVA_HOME=~/.jdks/<jdk-21> ./mvnw verify`.

**Frontend** (Node 24):

```bash
cd frontend
npm ci
npm start                       # http://localhost:4200, com proxy /api → :8080
npm run lint && npm run format:check && npm run test:ci && npm run build
```

## Configuração

Todas as variáveis estão em [`.env.example`](.env.example). Em produção
(`SPRING_PROFILES_ACTIVE=prod`): troque `JWT_SECRET` (≥ 32 caracteres aleatórios), as
senhas do banco e do RabbitMQ, configure o SMTP real e use `DEMO_USERS=false`. O Swagger
fica desligado em produção (`SWAGGER_ENABLED=true` para ligar).

Os **parâmetros de negócio** (limites das faixas, margens, prazos, metas) ficam no banco e
são alterados pela tela **Administração → Parâmetros**, com motivo e histórico. Quando sair o
decreto federal do ano, basta atualizar `band1_upper_limit` e `band2_upper_limit`.

## Documentação

- [Arquitetura](docs/ARQUITETURA.md)
- [Decisões e divergências com os documentos](docs/DECISOES.md)
- Documentos-fonte: `docs/fontes/` (texto extraído em `docs/texto/`)

## Estrutura

```
backend/    Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Flyway · RabbitMQ (outbox)
frontend/   Angular 22 · Angular Material · nginx
docs/       arquitetura, decisões, documentos do Contrata + NATA
.github/    CI (build + testes)
```
