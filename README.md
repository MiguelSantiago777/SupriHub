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
> semestral. Também há recurso contra o resultado do julgamento, convocação do próximo
> classificado quando a ordem não é assinada, sinal pago como parcela separada, contratação de
> urgência com ciência da Comissão e deslocamento ao final do rodízio por recusas injustificadas.

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

Todas as variáveis estão em [`.env.example`](.env.example). Em desenvolvimento só o
frontend fica aberto na rede; banco, RabbitMQ, MailHog e backend escutam em `127.0.0.1`
(`DEV_BIND=0.0.0.0` para abrir).

### Deploy em produção

```bash
./scripts/gerar-env-producao.sh --url https://suprihub.institutonata.org.br --admin-email admin@institutonata.org.br
# (Windows: .\scripts\gerar-env-producao.ps1 -Url ... -AdminEmail ...)
# preencha no .env.prod: SMTP (MAIL_*) e captcha (CAPTCHA_SITE_KEY/CAPTCHA_SECRET_KEY)
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

O script gera senhas do banco/RabbitMQ, o `JWT_SECRET` e a senha inicial do ADMIN (mostrada
uma vez). O `docker-compose.prod.yml` liga o perfil `prod`, tira as portas internas, remove o
MailHog e sobe o ClamAV. Com perfil `prod`, a aplicação **não sobe** se encontrar segredo de
exemplo/dev, `DEMO_USERS=true` ou senha de admin fraca. O HTTPS fica num proxy/LB à frente do
frontend (o cookie de sessão é `Secure`). O Swagger fica desligado (`SWAGGER_ENABLED=true` liga).

### Segurança da autenticação

- Access token JWT (HS256, 15 min) no header `Authorization: Bearer`, guardado só em memória
  no navegador; refresh token opaco em cookie `HttpOnly`/`SameSite=Strict`, rotacionado a cada
  uso (reuso fora da janela de 30 s encerra todas as sessões).
- Desativar o usuário, trocar seus perfis ou redefinir a senha invalida na hora os access
  tokens já emitidos.
- Verificação em duas etapas: depois da senha, um código de 6 dígitos chega por e-mail (vale
  10 minutos, 5 tentativas). Obrigatória para a equipe interna; o fornecedor liga em
  **Minha conta**. No ambiente local o código aparece no MailHog (http://localhost:8025).
- Senha errada: 5 tentativas por IP + e-mail bloqueiam aquele IP por 15 min (sem travar o dono
  da conta em outro IP); 20 tentativas de vários IPs bloqueiam a conta. As respostas não
  revelam se o e-mail existe.
- O nginx sobrescreve `X-Forwarded-For` e o backend só confia nele vindo da rede interna.

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
