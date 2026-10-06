# Decisões e divergências — SupriHub

Registro das decisões de projeto e das divergências encontradas entre o prompt de
especificação e os documentos do Contrata + NATA (`docs/fontes/`). **Regra geral: quando o
prompt conflita com os documentos, os documentos vencem.** Entre documentos, prevalece o
mais específico para o caso (Edital > Manual > Planilha, salvo indicação).

Formato: **D-NNN — título** · fase · status.

---

## 1. Fontes

**D-001 — Origem dos documentos** · Fase 0 · vigente
`./docs/` não existia. Os arquivos foram encontrados em `Downloads` e **copiados** (originais
intactos) para `docs/fontes/`. O texto extraído (para busca e conferência) está em
`docs/texto/`.

**D-002 — Versões do Edital** · Fase 0 · vigente (prompt §12)
O corpo do Edital (itens 1–12) vem de `02_Edital_Padrao..._.docx`; os anexos vêm da `V2`.
O seed do Catálogo (Anexo I) foi **gerado diretamente do texto da V2** (escopo, CNAE e PF),
não digitado à mão.

**D-003 — Planilha não é publicada** · Fase 0 · vigente
A planilha (`04_..._TESTE.xlsx`) tem dados de exemplo (nomes, telefones, e-mails fictícios)
e é ferramenta interna. Ela **não** está na página pública de documentos; o site publica
apenas Edital, Anexos, Política e Manual (Política art. 27).

## 2. Divergências entre prompt e documentos

**D-010 — Direito de cobrir: "inferior" × "igual ou inferior"** · Fase 2 · vigente
Edital 8.2: o pequeno negócio pode "apresentar proposta de **valor inferior** à primeira
colocada". A planilha (aba Cotações, "Como ler") diz "igual ou inferior" e o prompt diz
"cobrir o menor preço". **Sigo o Edital**: o parâmetro `cover_requires_strictly_lower = true`
exige valor estritamente menor. A Comissão pode mudar para `false` (igual basta).

**D-011 — Ordem do desempate e pontuação da planilha** · Fase 2 · vigente
Política art. 14, IV e Edital 8.4: mulher → mulher negra/CadÚnico → MEI → melhor
avaliação → sorteio. A pontuação da planilha
(`nível×1000 − mulher×100 − negra/CadÚnico×10 − MEI×1 − avaliação/100 + %acima`) **não é
lexicográfica** entre avaliação e %acima: uma avaliação 4,0 com 0% acima (−0,04) vence uma
avaliação 5,0 com 9% acima (+0,04). Decisão: o motor aplica a **ordem lexicográfica** dos
documentos e acrescenta "menor % acima" antes do sorteio (critério implícito da planilha e
coerente com a economicidade). O teste de paridade com a fórmula da planilha cobre os casos
em que as duas coincidem (inclusive o caso A/B/C do Manual), e um teste à parte documenta o
caso em que divergem.

**D-012 — Prazo de decisão do recurso de habilitação** · Fase 1 · vigente
Política art. 10 (tabela): "3 d.u. para recorrer; 5 d.u. para decidir". O Edital 5.2 e o
Manual (item 3, passo 6) dizem: Comissão reconsidera em 3 d.u. **ou** encaminha à Diretoria,
que decide em 5 d.u. Sigo o Edital/Manual (mais específicos). Parâmetros:
`appeal_filing_business_days=3`, `appeal_reconsideration_business_days=3`,
`board_appeal_decision_business_days=5`.

**D-013 — Certidão vencida: notificar antes de inativar** · Fase 1 · vigente
O prompt diz que certidões vencidas e não regularizadas levam a INATIVO_TEMPORARIO. A
Política art. 21, § 2º manda primeiro tentar renovar por consulta direta e, não sendo
possível, **notificar o credenciado para apresentar em 5 dias úteis**, sob pena de inativação.
O job diário notifica no vencimento e inativa só após o prazo
(`certificate_regularization_business_days=5`).

**D-014 — Descredenciamento por irregularidade (30 dias)** · Fase 1 · vigente
Política art. 23: perda de habilitação não regularizada em 30 dias leva a descredenciamento.
Como a decisão deve ser motivada, o sistema **gera alerta à Comissão** (parâmetro
`deregistration_after_irregular_days=30`) em vez de descredenciar automaticamente.

**D-015 — Territórios de atendimento × território da sede** · Fase 1 · vigente
O Anexo II da V2 **omite a seção 3 (Territórios de atendimento)**, mas o corpo do Edital
(3.1.1) exige "a indicação das linhas de fornecimento e dos territórios de interesse", e o
Anexo I diz que "o credenciado indica os municípios em que atende". Mantenho o campo.
Uso: o território **da sede**, calculado por edital, vale para a preferência local (Edital
8.3: "credenciados **sediados** no território..."). Os territórios **de atendimento** valem
para elegibilidade/convite. Fica guardado também o território-padrão (T1/T2/T3), como na
planilha.

**D-016 — Raça/cor: Anexo II × Anexo IV** · Fase 1 · vigente
O Anexo II lista preta, parda, indígena, branca, amarela, prefiro não informar; o Anexo IV
só pergunta "negra (preta ou parda)" e CadÚnico. Guardo a lista completa (Anexo II) e
derivo `mulherNegraOuCadUnico = (preta ∨ parda) ∨ CadÚnico`. O dado é facultativo, usado
só no desempate e em estatística agregada, e nunca é publicado individualmente.

**D-017 — Empate ficto só nas Faixas 2 e 3** · Fase 2 · vigente
A Política art. 14 diz "em todas as seleções"; o Edital 8.2 restringe às Faixas 2 e 3. Na
Faixa 1 não há disputa de preço (rodízio com teto do Banco de Preços), então o motor de
julgamento só roda nas Faixas 2 e 3. A **exclusividade** (8.1) vale para as Faixas 1 e 2.

**D-018 — Prazo de assinatura da OF** · Fase 3 · registrado
Edital 9.1: a OF/OS deve ser assinada em até 2 d.u., sob pena de convocar o próximo.
O parâmetro `service_order_signature_business_days=2` já existe.

**D-019 — Pessoa física maior de 18 anos** · Fase 1 · a implementar
Edital 2.1.3 exige PF autônoma maior de 18 anos. O requerimento de PF vai pedir a data de
nascimento (usada só nessa validação).

**D-020 — Esclarecimentos e impugnações** · fora do escopo atual
Edital 5.1 prevê resposta em 3 d.u. a impugnações e esclarecimentos por e-mail. Não está no
escopo das Fases 1–2; fica para depois, se pedido.

**D-021 — CNPJ de exemplo da planilha é inválido** · Fase 0 · informativo
`12.345.678/0001-90` (planilha e exemplo de máscara do prompt) tem dígito verificador
inválido. A **máscara** pública segue o formato do exemplo (`12.345.***/****-90`), mas os
dados de teste usam CNPJs válidos.

**D-022 — Requerimento eletrônico e declarações** · Fase 1 · vigente
O Anexo II vira formulário em 7 etapas, com rascunho salvo a qualquer momento. As
declarações dos Anexos III e V são aceitas eletronicamente: uma caixa de confirmação
registra data e hora em `declarations_accepted_at`. Marcar "negócio liderado por mulher"
vale como o Anexo IV. Os documentos do item 4.1 são enviados um por tipo (reenviar
substitui), em PDF, JPG ou PNG de até `upload_max_size_mb`. O tipo do arquivo é conferido
pelo conteúdo, não pela extensão.

**D-023 — "Requerimento completo" e prazo de análise** · Fase 1 · vigente
Edital 4.3: o prazo de 10 dias úteis corre do requerimento **completo**. Por isso o envio
só é aceito sem pendências (`ApplicationCompleteness`), entre elas: CPF/CNPJ válidos, PF
maior de 18 anos (D-019), PF só em linhas que aceitam PF, documentos obrigatórios do tipo e
declarações aceitas. Opcionais no envio: "se aplicável", "quando exigível" e
"recomendado"; a Comissão pede por ajuste quando a atividade exigir. O protocolo
(`CR-AAAA-NNNNN`) é atribuído no primeiro envio e mantido nos reenvios.

**D-024 — Ajuste, inabilitação e reenvio** · Fase 1 · vigente
O pedido de ajuste (Edital 4.3) devolve o requerimento ao fornecedor com prazo de
`diligence_business_days`. Ao reenviar, o prazo de análise é recalculado. A inabilitação
exige motivo. O inabilitado pode corrigir e reenviar, porque o credenciamento é permanente.
O recurso está em D-026. A prorrogação fiscal de pequenos negócios (5 + 5 d.u., Edital 4.4)
é feita como novo pedido de ajuste.

**D-025 — Quem analisa** · Fase 1 · vigente
A fila e as decisões ficam com a Comissão de Credenciamento e com o ADMIN
(`hasAnyRole('COMMISSION','ADMIN')`). Cada envio avisa por e-mail todos os membros ativos
da Comissão. A habilitação vale por `accreditation_validity_months` (24). A lista pública está
em D-027. A reconferência semestral de certidões e a atualização de dados do habilitado
(Edital 6.3) ficam para as próximas entregas da Fase 1.

**D-026 — Recurso contra a inabilitação** · Fase 1 · vigente
Edital 5.2 e Anexo X. O inabilitado recorre em até `appeal_filing_business_days` (3) dias
úteis da comunicação. Esse prazo é fixado no momento da inabilitação
(`supplier_application.appeal_deadline`). O recurso tem razões, pedido e um anexo
opcional. Cabe um recurso por decisão.

A Comissão **não nega** o recurso. Ela tem duas saídas:
- reconsidera em `appeal_reconsideration_business_days` (3): o recurso é provido e o
  fornecedor é habilitado por 24 meses;
- mantém a decisão e o **encaminha com motivação** à Diretoria, que provê ou nega em
  `board_appeal_decision_business_days` (5).

Enquanto o recurso corre, o requerimento fica bloqueado para edição. Negado o recurso, o
fornecedor pode corrigir e reenviar, porque o credenciamento é permanente. Quem age:
Comissão e ADMIN na reconsideração e no encaminhamento; Diretoria e ADMIN na decisão
final. A Diretoria entra direto em `/recursos`. Recurso contra seleção ou sanção (outras
etapas do Anexo X) fica para as Fases 2 e 3.

**D-027 — Cadastro de credenciados só para a equipe interna** · Fase 1 · vigente (decisão do usuário)
A Política (art. 27) e o Edital (4.6) mandam **publicar** a lista de credenciados no sítio.
Por decisão do Instituto, a lista fica **restrita** ao ADMIN e aos perfis internos que
operam o sistema: Comissão, Diretoria, Gestor da demanda, Financeiro e Controle Interno.
Divergência registrada para a Comissão avaliar a publicação por outro meio.

- **Onde:** página `/credenciados`, só com login interno (fornecedor recebe "acesso
  negado"), e `GET /api/suppliers`. Não há mais rota pública.
- **Quem aparece:** só habilitados com `accreditation_valid_until` vigente. Ao vencer, o
  fornecedor sai da lista sozinho. Filtros por linha, território e nome.
- **O que mostra:** nome, razão social, tipo, documento mascarado, município, territórios,
  linhas, validade, "liderado por mulher" e **contato** (telefone e e-mail), para a equipe
  acionar o fornecedor.
- **O que fica só no detalhe do requerimento:** dados bancários, raça/cor e CadÚnico
  (art. 28).
- **Autorização de divulgação:** como não há publicação, a caixa do Anexo II, item 5,
  saiu do formulário. A coluna `publish_woman_led` (V11) ficou sem uso.

**D-040 — Oportunidades: quem abre e o que o fornecedor vê** · Fase 2A · vigente (decisão do usuário)
- **Quem abre:** a Comissão (ou o ADMIN) cadastra e publica direto a parte 5 do DFD-NATA
  (Anexo VI): título, descrição, itens, linha, territórios, local e prazos. O fluxo completo
  do DFD, com pedido do gestor e atesto de saldo pelo Financeiro (partes 1 a 4 e 6), fica
  para depois.
- **Faixa:** sai do valor estimado e dos parâmetros vigentes (`BandPolicy`). O valor
  estimado **nunca** é exibido ao fornecedor.
- **Prazo mínimo para propostas:** `band2_min_proposal_business_days` (3) e
  `band3_min_proposal_business_days` (5), conferidos na publicação.
- **Quem participa:** fornecedor com a linha da oportunidade e algum território em comum.
  Se a oportunidade for exclusiva a mulheres, só negócio liderado por mulher. Nas Faixas 1
  e 2, só credenciado vigente. Na Faixa 3, também quem tem requerimento enviado ou em
  ajuste (D-033).
- **Onde aparece:** só na aba "Editais em aberto" de quem tem login, inclusive a Faixa 3.
  A Política (art. 27) manda publicar no sítio as chamadas da Faixa 3 e seus resultados;
  divergência registrada, como em D-027.

**D-041 — Rodízio da Faixa 1** · Fase 2A · vigente
Política art. 13, Manual 4.1 e D-034. Ao publicar, o sistema convida o primeiro da fila.
A ordem: quem nunca foi selecionado, depois quem está há mais tempo sem seleção, depois o
credenciamento mais antigo.
- O convidado tem `band1_acceptance_business_days` (2) para **aceitar enviando o preço**
  (proposta) ou recusar.
- Recusa, falta de resposta no prazo (job de hora em hora) ou preço recusado pela Comissão
  passam a vez ao seguinte.
- A Comissão **confere o preço e confirma** a seleção. A conferência automática contra o
  Banco de Preços (teto = média + 10%) entra quando o Banco existir.
- Sem ninguém na fila, a Comissão amplia o território (T2/T3, Manual 4.3).

**D-042 — Propostas e modelo do Anexo VII** · Fase 2A · vigente (pedido do usuário)
O fornecedor escolhe como enviar:
- **Preencher no sistema:** preço unitário de cada item; o total é calculado.
- **Modelo do Instituto:** Anexo VII em Word (.docx, gerado sem dependência externa) já
  preenchido com os dados da oportunidade e do fornecedor (porte, liderado por mulher e
  território da sede marcados). Ele preenche, assina e envia de volta (PDF, Word ou foto).
- **Proposta própria:** em PDF, Word ou foto.

Regras comuns:
- Nos dois modos com arquivo, o **valor total é digitado no sistema**, porque é ele que
  entra no julgamento.
- Validade mínima `proposal_validity_days` (30) e declaração do Anexo VII obrigatórias.
- Para MEI e pessoa física a tela recomenda o modelo; para as empresas, preencher no
  sistema.
- **Sigilo:** até o prazo, a Comissão só vê a quantidade de propostas. Depois vê o mapa
  ordenado pelo total, com o território da sede calculado para a oportunidade.
- Nas Faixas 2 e 3, o fornecedor pode substituir ou retirar a proposta até o prazo.

O julgamento (empate ficto, preferência local, desempate por equidade, direito de cobrir
e ata) é a Fase 2, parte B (D-043 a D-048).

**D-043 — Fluxo do julgamento (Faixas 2 e 3)** · Fase 2B · vigente
Manual 4.2, passos 5 e 6; Edital 7.5; checklist do dossiê, itens 7 e 8.
- Depois do prazo, a Comissão confere as propostas e clica em **Julgar**. A oportunidade
  passa a "Em julgamento" e o mapa comparativo é **congelado** (`judgment_entry`, imutável
  por trigger), com os parâmetros vigentes naquele momento.
- O motor (`JudgmentEngine`, puro) aplica menor preço → empate ficto (8.2) → preferência
  local (8.3) → mulher → mulher negra/CadÚnico → MEI → avaliação → menor % acima → sorteio
  (D-011).
- **Homologação:** na Faixa 2, a Comissão homologa; na Faixa 3, a **Diretoria aprova a ata**
  (ou devolve, o que anula o julgamento). Na homologação a ata em PDF é gerada, guardada e
  o SHA-256 vai para a trilha de auditoria. Antes disso, o PDF sai como minuta.
- **Anular:** a Comissão pode anular um julgamento em curso (erro, recurso acolhido); as
  propostas voltam a ficar disponíveis e pode-se julgar de novo. O histórico fica.
  Cancelar a oportunidade anula o julgamento em curso.
- O recurso contra o julgamento (Anexo VIII, "seleção/julgamento") ainda não tem tela; por
  ora, se acolhido, a Comissão anula e julga de novo.

**D-044 — Direito de cobrir, uma convocação por vez** · Fase 2B · vigente
Edital 8.2 e aba Cotações ("se não cobrir, o próximo da pontuação é convocado").
- Se a 1ª colocada não é a de menor preço, ela é convocada a oferecer valor **inferior** à
  menor proposta (D-010) em `cover_deadline_business_days` (1 d.u.).
- Recusa ou prazo vencido (job de hora em hora) passam a vez à próxima finalista, até chegar
  à de menor preço, que vence com o próprio valor.
- O convocado vê o valor a cobrir (é indispensável para cobrir); os demais não veem nada
  dos concorrentes até o resultado.

**D-045 — Sorteio feito pelo sistema, com semente** · Fase 2B · vigente
Edital 7.6 fala em "sorteio em sessão registrada, com convite aos empatados". O sistema
sorteia na hora do julgamento, com semente de `SecureRandom` gravada em `draw_record`
(imutável). Com a semente, qualquer pessoa reproduz o resultado, e a ata registra a semente
e a ordem. Empate exato é raro (exige igualdade em todos os critérios, inclusive o preço).
O sistema ainda não registra sorteio manual: se a Comissão fizer sessão presencial com as
empatadas, ela guarda a ata da sessão no dossiê. Divergência registrada.

**D-046 — Desclassificação e mínimo de propostas** · Fase 2B · vigente
- A Comissão desclassifica, com motivo, antes de julgar. É possível desfazer enquanto não
  houver julgamento em curso.
- Quem não tem credenciamento vigente na data do julgamento é desclassificado
  automaticamente, inclusive na Faixa 3 (D-033).
- Faixa 2 com menos de `band2_min_valid_proposals` (3) propostas válidas: a Comissão
  escolhe entre cancelar e repetir a cotação, ou **justificar** e julgar (a justificativa vai
  para a ata). Sem nenhuma proposta válida, não há julgamento.

**D-047 — Avaliação de desempenho ainda não existe** · Fase 2B · provisório
O critério "melhor avaliação" (8.4) conta como zero para todos até a Fase 3, que registra as
avaliações. O motor e o mapa já recebem o campo.

**D-048 — LGPD no julgamento** · Fase 2B · vigente
Política art. 28 e Anexo II, item 5.
- Só a equipe interna vê, no mapa, o indicador combinado "mulher negra ou CadÚnico". A raça
  e o NIS nunca saem do requerimento.
- A ata não tem coluna de raça/CadÚnico. O critério só aparece no motivo, quando foi ele que
  decidiu.
- O fornecedor vê só o valor vencedor, um motivo genérico ("empate ficto com desempate por
  equidade") e a própria posição, sem nomes dos concorrentes.

## 3. Pontos em aberto do prompt (§12) — padrões adotados

**D-030 — Visibilidade dos editais** · Fase 2 · padrão
A lista pública mostra Faixas 2 e 3; a Faixa 1 é interna (rodízio dirigido). Flag
`public_visibility` por edital (padrão definido pela faixa) para a Comissão ajustar.

**D-031 — Médio e grande porte** · Fase 0 · padrão
Edital 2.1.4: só nas linhas que o Anexo I admitir; o Anexo I só tem a coluna PF. Coluna
`supply_line.allows_medium_large` criada, **falsa em todas** (verificado por teste); a
Comissão liga por linha na API `PUT /api/admin/supply-lines/{code}`.

**D-032 — Preferência local para quem não é pequeno negócio** · Fase 2 · padrão
Planilha e Manual aplicam a preferência só entre as candidatas do empate ficto. Parâmetro
`local_preference_for_all = false`.

**D-033 — Faixa 3 e fornecedor ainda não credenciado** · Fase 2 · padrão
Pode enviar proposta se tiver requerimento enviado; sem habilitação até a data-limite, a
proposta é desclassificada (registrada com o motivo).

**D-034 — Ordem do rodízio** · Fase 3 · padrão
Política art. 13: "ordem do cadastro"; planilha e Manual: há mais tempo sem contratação
(nunca contratado primeiro; empate = credenciamento mais antigo). Padrão: planilha.

**D-035 — Município de execução fora do cadastro** · Fase 0 · padrão
É tratado como "T3": não confere T1 a ninguém (só a sede em Mesquita continua T1), e
`TerritoryResolver` devolve `executionMunicipalityUnknown = true` para a Comissão ser
avisada. O cadastro tem os 92 municípios do RJ, então o caso só ocorre fora do estado.

**D-036 — Nome** · Fase 0 · padrão
Marca "SupriHub"; textos institucionais citam "Contrata + NATA — Instituto NATA".

## 4. Decisões técnicas

**D-050 — RabbitMQ + transactional outbox** · Fase 0 · vigente (pedido do usuário)
Notificações e eventos de domínio passam por outbox + RabbitMQ:
- `DomainEvents.publish()` grava em `outbox_event` **na mesma transação** da mudança
  (`Propagation.MANDATORY`); nada é enviado ao broker dentro da requisição HTTP.
- `OutboxRelay` publica em lotes (`FOR UPDATE SKIP LOCKED`, várias instâncias seguras) no
  exchange topic `suprihub.events`, com *publisher confirms*, backoff exponencial (máx.
  1 h) e status `FAILED` após 10 tentativas (o ADMIN reprocessa em `/api/admin/outbox`).
- Entrega **at-least-once**; consumidores idempotentes via `processed_message`.
- Falhas do consumidor: 4 tentativas e depois DLQ (`suprihub.notifications.dlq`).
- **LGPD:** o payload dos eventos carrega só IDs. `EventPayloadGuard` rejeita chaves como
  email, cpf, telefone, raça e NIS. O token de confirmação de e-mail é gerado **no
  consumidor**, nunca trafega pelo broker.
- Eventos publicados são apagados após 30 dias.

**D-051 — Angular Material (e não PrimeNG)** · Fase 0 · vigente
Mantido pelo time do Angular (mesmo ciclo de versões), com CDK de acessibilidade
(FocusTrap, LiveAnnouncer) e componentes WAI-ARIA. Tem menos dependências, e o público
pede interface simples, não muitos widgets. Tema M3 com tokens próprios de contraste AA.

**D-052 — Fontes e ícones self-hosted** · Fase 0 · vigente
Atkinson Hyperlegible (projetada para baixa visão) e Material Icons via `@fontsource`. Não
há chamada a CDN externo: nada vaza de navegação para terceiros, e o CSP fica restrito a
`'self'`.

**D-053 — Sem Lombok** · Fase 0 · vigente
Records para DTOs; entidades com métodos de domínio explícitos. Evita a fragilidade do
processamento de anotações com JDKs novos.

**D-054 — JWT** · Fase 0 · vigente
Access token HS256 de 15 min, só em memória no frontend, com claims mínimas (sub = id,
roles; sem dados pessoais). Refresh token opaco de 7 dias, guardado como SHA-256, em cookie
`HttpOnly`, `SameSite=Strict`, `Path=/api/auth`. É **rotacionado a cada uso**; o reuso de
um token já rotacionado encerra todas as sessões do usuário. Um cookie auxiliar sem segredo
(`suprihub_session=1`) só indica que há sessão, para o frontend não chamar `/refresh` em
toda visita anônima.

**D-055 — Cadastro não revela se o e-mail existe** · Fase 0 · vigente
O cadastro, o "esqueci a senha" e o reenvio de confirmação respondem sempre igual. Se o
e-mail já existe, o titular recebe por e-mail o aviso "você já tem cadastro". Login com
e-mail não confirmado é bloqueado com orientação para reenviar o link. A conta é bloqueada
por 15 min após 5 senhas erradas.

**D-056 — Usuários internos por convite** · Fase 0 · vigente
O autocadastro cria só SUPPLIER. Os perfis internos são criados pelo ADMIN e recebem um link
para definir a própria senha, de modo que o ADMIN nunca conhece a senha. O primeiro ADMIN
vem de `BOOTSTRAP_ADMIN_EMAIL/PASSWORD`. Em dev, há um usuário de demonstração por perfil.

**D-057 — Health check sem SMTP** · Fase 0 · vigente
`management.health.mail.enabled=false`: como o e-mail é assíncrono, o SMTP fora do ar não
deve derrubar a prontidão da API.

**D-058 — Calendário de dias úteis** · Fase 0 · vigente
Edital 12.2: exclui o dia do início, inclui o do vencimento, e prazos só começam e vencem
em dia útil. Contam os feriados nacionais, os estaduais (RJ) e os municipais **da sede
(Mesquita)**. Carnaval, Quarta de Cinzas e Corpus Christi entram como pontos facultativos
não úteis (parâmetro `optional_points_are_non_business`). Seed com 2026–2027. **Conferir
com a Prefeitura de Mesquita** os feriados municipais além do aniversário (25/09), como a
padroeira, e cadastrá-los na tela de feriados.

**D-059 — Região Metropolitana do RJ** · Fase 0 · vigente
22 municípios pela LC estadual nº 184/2018: Belford Roxo, Cachoeiras de Macacu, Duque de
Caxias, Guapimirim, Itaboraí, Itaguaí, Japeri, Magé, Maricá, Mesquita, Nilópolis, Niterói,
Nova Iguaçu, Paracambi, Petrópolis, Queimados, Rio Bonito, Rio de Janeiro, São Gonçalo, São
João de Meriti, Seropédica e Tanguá. Os códigos IBGE vêm da API de Localidades do IBGE.

**D-060 — Tabelas da Fase 3** · Fase 0 · ajustado
O plano previa criar já na Fase 0 as tabelas mínimas da Fase 3 (contract,
supplier_evaluation, price_bank_entry, sanction_process, dossier_checklist). Como elas têm
FKs para `supplier` e `tender`, entram nas migrações das Fases 1 e 2, junto com essas
tabelas, sem uso pela aplicação até a Fase 3.

**D-061 — Versões** · Fase 0 · vigente
Spring Boot 3.5.16 (último 3.x, como pedido), Java 21 (o build local usa o JDK 21 em
`~/.jdks`, porque o PATH tem o JDK 27), Angular 22.2, PostgreSQL 16, RabbitMQ 4.

**D-062 — Rate limit** · Fase 0 · vigente
Bucket4j em memória, por IP: 120 req/min em `/api/public/**` e 20 req/min em
`/api/auth/**`, com resposta 429 + `Retry-After` em ProblemDetail. Com várias instâncias,
trocar por Bucket4j com Redis ou JDBC.

**D-063 — Identidade visual** · Fase 0 · vigente
Visual moderno derivado do logo do Instituto NATA (azul ciano + verde):
- Títulos em Plus Jakarta Sans; corpo em Atkinson Hyperlegible, 18px.
- Cabeçalho branco translúcido fixo, cards com raio de 16px e botões em pílula.
- Hero com a "jornada do credenciamento", catálogo em grade de cards e telas de acesso em
  duas colunas.
- Gradiente decorativo (`--sh-gradient`) só onde não há texto. Texto branco sobre gradiente
  usa `--sh-gradient-deep` (todas as paradas com contraste ≥ 5:1).

**D-064 — Guard de visitante** · Fase 0 · corrigido
`guestGuard` estava em `canMatch` de uma rota com `path: ''`, que casa com qualquer URL.
Com o usuário logado, cada navegação redirecionava de novo para a própria área, em loop
infinito: o login "ficava entrando para sempre". Agora o guard roda em `canActivateChild`,
e `app.routes.spec.ts` testa a navegação real com prazo para pegar o loop.
