# SupriHub

Sistema de credenciamento de fornecedores e editais do **Contrata + NATA — Instituto NATA**
(Mesquita/RJ). Substitui a Planilha de Gestão e abre o processo ao público, com
prioridade a MEIs, pequenos negócios, cooperativas, autônomos e negócios liderados por
mulheres.

> Estado atual: **Fase 0** concluída (esqueleto, autenticação, catálogo, parâmetros,
> calendário, auditoria, mensageria). **Fase 1** concluída: requerimento de
> credenciamento pelo fornecedor, análise pela Comissão (habilitar, pedir ajuste,
> inabilitar), recurso à Comissão/Diretoria, cadastro de credenciados (uso interno) e
> manutenção do cadastro (validade das certidões com aviso "RENOVAR", inativação temporária
> e reativação, reconferência semestral, atualização cadastral e renovação aos 24 meses). **Fase 2**
> (editais): a Comissão publica oportunidades, a Faixa 1 roda por rodízio, o fornecedor envia
> propostas em "Editais em aberto" e, nas Faixas 2 e 3, a Comissão julga (empate ficto,
> preferência local, desempate por equidade, sorteio e direito de cobrir), homologa (a
> Diretoria aprova a ata da Faixa 3) e a ata sai em PDF. **Fase 3** começou: a ordem de
> fornecimento nasce na adjudicação, é emitida e assinada eletronicamente, o gestor registra o
> Termo de Recebimento e Avaliação, o fornecedor envia a nota fiscal e o Financeiro registra o
> pagamento; o Banco de Preços é alimentado no recebimento e confere o teto do rodízio.
> Controle: processo sancionador com defesa e recurso (suspensão tira o fornecedor das
> oportunidades), dossiê do Anexo XIII com revisão por amostragem e indicadores do relatório
> semestral.

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
abra o link de confirmação no MailHog. Depois, preencha e envie o requerimento em
"Minha área". Para analisar, entre como `commission@suprihub.local` (ou como ADMIN) e
abra **Requerimentos**. Recursos ficam em **/recursos** (Comissão e Diretoria,
`board@suprihub.local`), e o cadastro de credenciados em **/credenciados** (só equipe interna).
Certidões, reconferência e renovações ficam em **Manutenção** (`/comissao/manutencao`); o
credenciado atualiza dados, certidões e pede a renovação na própria área.

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
