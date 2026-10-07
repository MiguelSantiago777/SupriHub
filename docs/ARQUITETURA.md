# Arquitetura — SupriHub

Sistema web do **Contrata + NATA — Instituto NATA**: credenciamento permanente de
fornecedores e publicação/julgamento de editais de compra, substituindo a Planilha de
Gestão.

## Visão geral

```
                         ┌──────────────────────────── docker compose ────────────────────────────┐
 Navegador ──HTTPS──▶  frontend (nginx)                                                           │
 (Angular SPA)         │  /            → SPA (Angular 22, Material)                               │
                       │  /api, /swagger-ui, /actuator/health → proxy                             │
                       ▼                                                                          │
                   backend (Spring Boot 3.5, Java 21) ──JDBC──▶ postgres 16 (Flyway)              │
                       │   │                                    ▲                                 │
                       │   └── outbox_event (mesma transação) ──┘                                 │
                       │            │ OutboxRelay (FOR UPDATE SKIP LOCKED, confirms)              │
                       │            ▼                                                             │
                       │      rabbitmq 4  exchange topic "suprihub.events"                        │
                       │            │  auth.# supplier.# tender.# judgment.#                      │
                       │            ▼                                                             │
                       └── NotificationEventListener (idempotente) ──SMTP──▶ mailhog (dev)        │
                                                                                                  │
                         volume "files" ◀── FileStorage (local; trocável por S3)                  │
                         └────────────────────────────────────────────────────────────────────────┘
```

## Backend — módulos

Pacote base `org.institutonata.suprihub`. Cada módulo segue a mesma divisão:

| Camada | Conteúdo | Regras |
|---|---|---|
| `api` | Controllers finos e DTOs (records) | Não acessa repositórios (ArchUnit) |
| `application` | Services transacionais, orquestração, auditoria, eventos | |
| `domain` | Entidades e **regras puras** (sem Spring) | Testáveis sem contexto (ArchUnit) |
| `infra` | Repositórios, integrações (SMTP, RabbitMQ, segurança) | |

| Módulo | Responsabilidade | Fase |
|---|---|---|
| `shared` | ProblemDetail (RFC 7807), validadores BR (CPF/CNPJ/CEP/telefone), máscara LGPD, `BusinessDayCalendar`, `FileStorage`, ganchos de antivírus/captcha, rate limit, paginação | 0 |
| `auth` | Usuários, perfis, JWT + refresh rotativo, confirmação de e-mail, convite, redefinição de senha | 0 |
| `catalog` | Linhas do Anexo I, municípios do RJ, `TerritoryResolver` | 0 |
| `parameter` | Parâmetros tipados com histórico; feriados → calendário | 0 |
| `audit` | `audit_log` imutável (trigger), antes/depois em JSON | 0 |
| `messaging` | Transactional outbox, relay, topologia RabbitMQ, idempotência | 0 |
| `notification` | `MessageChannel` (e-mail; WhatsApp previsto), templates Thymeleaf pt-BR | 0 |
| `tender` | `BandPolicy` (faixas) → DFD/edital, máquina de estados | 0 → 2 |
| `supplier` | Requerimento, documentos, habilitação, cadastro, recursos; manutenção do credenciado: validade das certidões (`CertificateRegularity` puro), job diário de aviso e inativação temporária, reconferência semestral, atualização cadastral e renovação | 1 |
| `proposal` | Propostas com sigilo até o prazo | 2 |
| `judgment` | `JudgmentEngine` **puro** (`domain`), entidades em `model`, mapa comparativo, direito de cobrir, sorteio com semente, ata PDF (OpenPDF), homologação Comissão/Diretoria | 2 |
| `contract` | Ordem de Fornecimento/Serviço (Anexo VIII) com PDF, assinatura eletrônica, Termo de Recebimento e Avaliação (Anexo IX), NF/RPA, pagamento, Banco de Preços (consulta e teto do rodízio), dossiê (Anexo XIII); `SupplierRatings` alimenta o desempate | 3 |
| `sanction` | Processo sancionador (Anexo XII): defesa, decisão, recurso à Diretoria, bloqueio do cadastro e inativação cautelar; job de prazos | 3 |
| `report` | Indicadores do relatório semestral (metas de inclusão, DFD→OF, avaliação, dossiês) | 3 |

### Regras transversais

- **Parâmetros nunca no código:** `ParameterKey` só define nome e tipo; o valor vem da
  tabela `parameter`. A aplicação não sobe se faltar alguma chave. Alterações exigem motivo
  e geram `parameter_history` + `audit_log`.
- **Dias úteis:** `BusinessDayCalendar` (puro), alimentado pelo `CalendarService` com
  feriados nacionais, do RJ e de Mesquita.
- **Território por edital:** `TerritoryResolver.resolve(sede, municípioDeExecução)` →
  T1/T2/T3/fora do estado.
- **Auditoria:** chamada explícita pelos services, com snapshots em DTO (nunca entidades).
  `UPDATE`/`DELETE` em `audit_log` são bloqueados por trigger.
- **LGPD:** DTOs públicos separados, sem campos sensíveis. `toString()` seguro em DTOs e
  entidades. E-mails não aparecem em log (verificado nos testes). Eventos só carregam IDs
  (`EventPayloadGuard`).
- **Erros:** `GlobalExceptionHandler` → `ProblemDetail` com `code` estável e `errors[]`
  por campo; textos em pt-BR; nunca stack trace.
- **Concorrência:** `@Version` nas entidades editáveis; conflito → 409.

### Mensageria (outbox)

1. O service muda o estado e chama `DomainEvents.publish(event)`: um INSERT em
   `outbox_event` **na mesma transação**.
2. `OutboxRelay` (a cada 2 s) trava um lote de PENDING com `SKIP LOCKED`, publica no
   exchange `suprihub.events` (routing key = tipo do evento), espera o *confirm* e marca
   PUBLISHED. Em falha: backoff exponencial; depois de 10 tentativas, FAILED.
3. `NotificationEventListener` consome `suprihub.notifications`. Com `IdempotentConsumer`,
   cada mensagem é processada uma vez só. Busca os dados no banco, renderiza o template e
   envia. Exceção: 4 tentativas e depois DLQ.

Convenção de eventos: `<módulo>.<agregado>.<fato>`, ex.: `auth.user.registered`.

## Frontend

Angular 22 com componentes standalone, signals, formulários reativos tipados e
`withComponentInputBinding`. Lazy loading por área (`/fornecedor`, `/comissao`, `/gestor`,
`/financeiro`, `/controle`, `/diretoria`, `/admin`), protegida por `roleGuard`.

```
src/app/
  core/      auth (signals, interceptor com refresh, guards), http (ProblemDetail, interceptor de erro), api
  public/    início, catálogo, edital e anexos, páginas de status
  auth/      entrar, criar conta, confirmar e-mail, esqueci/nova senha
  areas/     área do fornecedor, áreas internas, admin (parâmetros, usuários)
```

- **Sessão:** o access token fica em memória; o refresh vai em cookie HttpOnly. No 401, o
  interceptor renova uma vez (com uma única chamada concorrente) e repete a requisição.
- **pt-BR:** `LOCALE_ID pt-BR`, moeda `BRL`, títulos de página "<página> — SupriHub".
- **Acessibilidade (WCAG AA):** fonte Atkinson Hyperlegible com base 18px, contraste ≥ 4,5:1,
  foco visível forte, "pular para o conteúdo", foco no `<main>` a cada navegação, layout
  mobile-first, botões de 52px, mensagens de erro por campo, mostrar/esconder senha,
  `prefers-reduced-motion`, lint de acessibilidade de templates (angular-eslint).

## Modelo de dados

Migrações Flyway em `backend/src/main/resources/db/migration`:

| Versão | Conteúdo |
|---|---|
| V1 | Núcleo: role, app_user, user_role, refresh_token, email_verification_token, supply_line, municipality, parameter(+history), business_holiday, audit_log (imutável), notification, outbox_event, processed_message, shedlock |
| V2 | 20 linhas do Anexo I (escopo, CNAE, PF) |
| V3 | Parâmetros (aba Parâmetros + prazos do Edital/Política) |
| V4 | 92 municípios do RJ (IBGE) com flag da Região Metropolitana |
| V5 | Feriados 2026–2027 (nacionais, RJ, Mesquita) |
| V10 | Fase 1 — requerimento (Anexo II), linhas, territórios, documentos e andamento |
| V11 | Fase 1 — recurso da inabilitação (e coluna de autorização de divulgação, sem uso — D-027) |
| V20 | Fase 2A — oportunidades, itens, territórios, convites do rodízio, propostas |
| V21 | Fase 2B — desclassificação, julgamento, mapa (imutável), sorteio (imutável), direito de cobrir |
| V22 | Fase 3A — ordem de fornecimento/serviço, itens, termos de recebimento, arquivos, banco de preços |
| V23 | Fase 3B — bloqueio do cadastro, processo sancionador e arquivos, dossiê (itens manuais) e revisões |
| V24 | Fase 1 — validade das certidões, inativação temporária, reconferência semestral e renovação (numerada depois da V23 porque o Flyway não aplica versões fora de ordem) |
| V25 | Lacunas do fluxo — recurso contra o julgamento, várias ordens por oportunidade (próximo classificado), sinal, contratação de urgência e deslocamento no rodízio |

## Testes

- **Unitários** (`*Test`): validadores, máscara, calendário, território, faixas, outbox,
  rate limit, ArchUnit.
- **Integração** (`*IT`, failsafe): Testcontainers com PostgreSQL 16 e RabbitMQ 4 reais.
  Cobrem o fluxo completo de cadastro → outbox → broker → e-mail → confirmação → login →
  refresh rotativo com detecção de reuso; controle de acesso por perfil; mudança de
  parâmetro alterando a faixa; seeds conferidos contra os documentos; imutabilidade da
  auditoria.
- **Frontend** (Vitest): interceptor (token + refresh), guards, catálogo, ProblemDetail.
