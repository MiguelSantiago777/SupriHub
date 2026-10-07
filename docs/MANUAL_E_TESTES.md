# SupriHub: manual de uso e roteiro de testes

![Logo do SupriHub](img/suprihub-logo.png){width=50%}

Este documento tem duas partes:

- **Parte 1, Manual de uso:** o que cada perfil de acesso faz no sistema e onde fica cada função.
- **Parte 2, Roteiro de testes:** todos os testes a executar, numerados, com passos e resultado esperado. Marque cada teste como Passou ou Falhou e anote o que viu.

O SupriHub é o sistema de credenciamento de fornecedores e de editais do **Contrata + NATA, do Instituto NATA** (Mesquita/RJ). Ele substitui a Planilha de Gestão. A prioridade é para MEIs, pequenos negócios, cooperativas, autônomos e negócios liderados por mulheres.

# Preparação do ambiente de testes

## Endereços

| O quê | Endereço |
|---|---|
| Sistema | http://localhost:4200 (ou o IP da máquina do servidor, porta 4200) |
| Caixa de e-mails de teste (MailHog) | http://localhost:8025 |

Todos os e-mails que o sistema envia (confirmação de cadastro, convites, avisos, nova senha) **não saem para a internet**: eles caem no MailHog. Abra o MailHog para clicar nos links.

O MailHog só abre na própria máquina que roda o sistema. Se você for testar de outro computador, peça para quem cuida do servidor subir o ambiente com `DEV_BIND=0.0.0.0`.

## Usuários de teste

Já existe um usuário pronto para cada perfil:

| Perfil | E-mail | Senha |
|---|---|---|
| Administração (TI) | admin@suprihub.local | Admin@12345 |
| Fornecedor | supplier@suprihub.local | Senha@123 |
| Comissão de Credenciamento | commission@suprihub.local | Senha@123 |
| Gestor da demanda | demand-manager@suprihub.local | Senha@123 |
| Financeiro | finance@suprihub.local | Senha@123 |
| Controle Interno | internal-control@suprihub.local | Senha@123 |
| Diretoria | board@suprihub.local | Senha@123 |

Para os fluxos completos de fornecedor, crie também **fornecedores novos** pelo "Quero me credenciar" (use e-mails inventados, como maria.teste1@exemplo.com). Assim você testa o cadastro do zero e consegue ter vários fornecedores concorrendo no mesmo edital.

**Dica:** use uma janela anônima (ou outro navegador) para cada usuário. Assim você fica logado como Comissão numa janela e como Fornecedor na outra ao mesmo tempo.

## Dados de teste

CPF e CNPJ precisam ser válidos (o sistema confere o dígito verificador). Use estes:

| Tipo | Números válidos para teste |
|---|---|
| CPF | 123.456.789-09 · 987.654.321-00 · 111.444.777-35 · 529.982.247-25 |
| CNPJ | 11.222.333/0001-81 · 45.789.123/0001-91 · 33.669.912/0001-00 · 77.112.233/0001-00 |

Tenha numa pasta estes arquivos:

- um **PDF pequeno** (qualquer documento), um **JPG** e um **PNG**;
- um **arquivo Word (.docx)**;
- um **arquivo maior que 10 MB**;
- um arquivo de texto (.txt) **renomeado para .pdf** (para testar se o sistema percebe que não é PDF de verdade).

## Como registrar um problema

Para cada teste que falhar, anote:

1. o **número do teste** (ex.: FOR-07);
2. com qual **usuário** estava logado;
3. o que fez, passo a passo;
4. o que **esperava** e o que **aconteceu**;
5. um **print da tela** (com a mensagem de erro, se houver), o navegador usado e o horário.

## Prazos e dias úteis

Quase todos os prazos do sistema são em **dias úteis**, contados como no Edital: não conta o dia do início, conta o dia do vencimento, e só vence em dia útil. Feriados nacionais, estaduais (RJ) e municipais de Mesquita não contam.

Alguns testes dependem de um prazo vencer (por exemplo, "o fornecedor não assinou em 2 dias úteis"). Eles estão marcados com **[prazo]**. Não é para esperar dias: combine com o desenvolvedor, que consegue "adiantar o relógio" daquele registro no banco de dados para você ver o resultado.

# Parte 1: Manual de uso

## Conceitos que aparecem em todo o sistema

**Linhas de fornecimento.** São as categorias do Anexo I do Edital (alimentação, eventos, serviços gráficos etc.). O fornecedor escolhe em quais linhas quer ser credenciado. Algumas linhas aceitam pessoa física (autônomo).

**Territórios.** T1 é Mesquita, T2 é a Região Metropolitana do Rio (22 municípios) e T3 é o restante. O fornecedor informa em quais municípios atende. O território da **sede** dá preferência local no julgamento.

**Tipos de negócio.** MEI, Microempresa (ME), Empresa de Pequeno Porte (EPP), Cooperativa e Profissional autônomo (pessoa física, maior de 18 anos).

**Faixas de valor** (valores de 2026, alteráveis em Parâmetros):

| Faixa | Valor estimado | Como o fornecedor é escolhido |
|---|---|---|
| Faixa 1 | até R$ 13.098,41 | Rodízio: o sistema convida um credenciado por vez |
| Faixa 2 | até R$ 65.492,11 | Cotação: vários enviam proposta e a Comissão julga |
| Faixa 3 | acima de R$ 65.492,11 | Chamada: propostas, julgamento e a Diretoria aprova a ata |

**Situação do credenciamento.** O requerimento passa por: em preenchimento → em análise → aguardando ajuste (se a Comissão pedir) → habilitado ou inabilitado. Habilitado vale 24 meses. Com certidão vencida e não regularizada, o credenciado fica **inativo temporário** e sai das oportunidades até regularizar.

## Matriz de acesso (quem vê o quê)

| Tela (menu) | Fornecedor | Comissão | Gestor | Financeiro | Controle | Diretoria | Admin |
|---|---|---|---|---|---|---|---|
| Início, Catálogo, Edital e anexos | sim | sim | sim | sim | sim | sim | sim |
| Meu credenciamento, Editais em aberto, Meus contratos | sim | não | não | não | não | não | não |
| Requerimentos, Manutenção, Editais (gestão) | não | sim | não | não | não | não | sim |
| Contratos (ordens de fornecimento) | não | sim | sim | sim | consulta | consulta | sim |
| Contratação de urgência | não | sim | sim | não | não | não | sim |
| Preços (Banco de Preços) | não | sim | sim | sim | sim | sim | sim |
| Sanções | não | sim | não | não | consulta | sim | sim |
| Dossiês (revisão por amostragem) | não | não | não | não | sim | não | sim |
| Indicadores | não | sim | não | sim | sim | sim | sim |
| Atas (aprovação da Faixa 3) | não | não | não | não | não | sim | não |
| Recursos | não | sim | não | não | não | sim | sim |
| Credenciados (lista interna) | não | sim | sim | sim | sim | sim | sim |
| Administração (usuários e parâmetros) | não | não | não | não | não | não | sim |

Uma tela que o perfil não pode abrir mostra **"Acesso negado"**. Isso vale também para quem digita o endereço direto no navegador.

## Visitante (sem login)

- **Início:** apresentação do programa e a jornada do credenciamento.
- **Catálogo:** todas as linhas de fornecimento, com filtro "Aceitam pessoa física".
- **Edital e anexos:** documentos públicos para baixar (Edital, Anexos, Política, Manual).
- **Quero me credenciar:** cria a conta de fornecedor (nome, e-mail, senha). O sistema envia um link de confirmação por e-mail; só depois de confirmar dá para entrar.
- **Entrar**, **Esqueci minha senha** e **Reenviar confirmação de e-mail**.

A senha precisa ter de 8 a 72 caracteres, com letras e números.

## Fornecedor

O menu do fornecedor tem **Meu credenciamento**, **Editais em aberto** e **Meus contratos**.

### Meu credenciamento: preencher e enviar o requerimento

O requerimento tem 7 etapas. Dá para clicar em **Salvar rascunho** a qualquer momento e voltar depois.

1. **Tipo de negócio:** MEI, ME, EPP, Cooperativa ou Autônomo.
2. **Identificação e contato:** CPF ou CNPJ, nome, endereço, telefone, representante. Autônomo informa a data de nascimento (precisa ter 18 anos ou mais).
3. **Linhas e territórios:** em quais linhas quer atuar e em quais municípios atende. Autônomo só pode escolher linhas que aceitam pessoa física.
4. **Dados para pagamento:** banco, agência e conta, ou chave Pix, do próprio titular.
5. **Equidade (facultativo):** negócio liderado por mulher, raça/cor, CadÚnico. Serve só para o desempate e nunca é publicado.
6. **Documentos:** envio de um arquivo por tipo (PDF, JPG ou PNG, até 10 MB). Certidões pedem a data "válida até". Reenviar um tipo substitui o anterior.
7. **Declarações e envio:** aceitar as declarações (Anexos III e V) e enviar.

O botão de envio só funciona quando não falta nada: a tela lista as pendências. No envio o requerimento recebe um protocolo no formato CR-AAAA-NNNNN e a Comissão tem 10 dias úteis para analisar.

### Depois do envio

- **Em análise:** só acompanhar.
- **A Comissão pediu um ajuste:** a tela mostra o que corrigir e o prazo. Corrija e envie de novo.
- **Habilitado ("Você está credenciado!"):** passa a receber oportunidades. Aparece a área de manutenção (abaixo).
- **Inabilitado:** a tela mostra o motivo e o botão **Recorrer da decisão** (prazo de 3 dias úteis). O recurso tem razões, pedido e um anexo opcional. Também é possível corrigir e reenviar.

### Manutenção do cadastro (fornecedor habilitado)

- **Certidões:** mostra a validade de cada uma. Perto do vencimento aparece o aviso "RENOVAR". Envie a certidão nova pela própria tela.
- **Atualizar dados:** endereço, contato, representante, conta bancária e documentos, sem novo requerimento. Linhas, territórios, tipo de negócio e CPF/CNPJ só a Comissão muda.
- **Renovação:** abre 60 dias antes do fim dos 24 meses. Confirme as declarações e clique em **Pedir renovação**.

### Editais em aberto

A tela separa **Convites para você**, **Abertos para você** e **Encerrados e respondidos**. Só aparecem oportunidades da linha e de território em comum com o fornecedor.

- **Faixa 1 (rodízio), "Chegou a sua vez":** em 2 dias úteis, **Aceitar e enviar o preço** ou **Recusar convite**. Ao recusar, dá para informar o motivo (indisponibilidade). Duas recusas sem motivo seguidas jogam o fornecedor para o fim da fila.
- **Faixas 2 e 3, enviar proposta:** três formas:
  - **preencher no sistema** o preço de cada item (o total é calculado);
  - **baixar o modelo do Instituto** (Anexo VII em Word, já preenchido), assinar e enviar de volta;
  - **enviar proposta própria** (PDF, Word ou foto).

  Nos dois modos com arquivo, o valor total também é digitado no sistema. Validade mínima de 30 dias e declaração obrigatória. Até o prazo dá para **Alterar proposta** ou **Retirar proposta**.
- **Direito de cobrir:** se o fornecedor for o primeiro colocado por critério de preferência mas não tiver o menor preço, ele é convidado a **Enviar novo valor** (menor que o menor preço) em 1 dia útil, ou **Não quero cobrir**.
- **Resultado:** depois da homologação, o fornecedor vê o valor vencedor e a própria posição, sem nomes dos concorrentes. Quem não venceu pode **Recorrer do resultado** em 3 dias úteis.

### Meus contratos

- **Assinar eletronicamente** a ordem de fornecimento em 2 dias úteis (Faixas 2 e 3). Na Faixa 1, o aceite do rodízio já vale como assinatura.
- Baixar a **Ordem em PDF**.
- Ver o **Termo de Recebimento e Avaliação** e clicar em **Dar ciência do termo**.
- **Enviar nota fiscal** (número, data, valor e arquivo) depois do recebimento definitivo.
- Acompanhar sinal e pagamento.

### Processos (sanções)

Se a Comissão abrir um processo sancionador, ele aparece para o fornecedor com o prazo. O fornecedor pode **Enviar defesa** (5 dias úteis) e, depois da decisão, **Enviar recurso** à Diretoria.

## Comissão de Credenciamento

O menu tem **Requerimentos**, **Manutenção**, **Editais**, **Contratos**, **Preços**, **Sanções**, **Indicadores**, **Recursos** e **Credenciados**.

### Requerimentos

- As abas são **Em análise**, **Aguardando ajuste**, **Habilitados**, **Inabilitados** e **Em preenchimento**, com busca.
- No detalhe do requerimento aparecem todos os dados, os documentos (botão **Abrir**) e o andamento.
- A decisão tem três opções:
  - **Habilitar:** credencia por 24 meses;
  - **Pedir ajuste:** devolve ao fornecedor com prazo de 5 dias úteis;
  - **Inabilitar:** exige motivo.
- A Comissão pode anexar uma certidão obtida no site oficial e corrigir a validade dos documentos.

### Manutenção

Abas **Certidões**, **Reconferência semestral**, **Renovações** e **Inativos**:

- marcar certidão como **Conferida** e **Registrar reconferência** (a cada 6 meses);
- **Renovar** (mais 24 meses) ou **Devolver** o pedido de renovação com o que falta;
- acompanhar os inativos temporários. A reativação é automática quando as certidões ficam em dia.

### Editais (oportunidades)

1. **Nova oportunidade:** título, descrição, itens (descrição, unidade, quantidade), linha, territórios, local, datas, valor estimado e prazo de propostas. Clique em **Salvar rascunho**.
2. A faixa é calculada pelo valor estimado. O valor estimado nunca aparece para o fornecedor.
3. **Publicar:** confere o prazo mínimo (3 dias úteis na Faixa 2 e 5 na Faixa 3).
4. **Faixa 1, rodízio:** o sistema convida o primeiro da fila. Quando o convidado envia o preço, a tela mostra o teto do Banco de Preços (média + 10%) e marca "acima" ou "dentro do teto". A Comissão escolhe **Confirmar seleção** ou **Recusar preço** (que chama o próximo). Se acabar a fila, use **Ampliar e convidar** (T2/T3).
5. **Faixas 2 e 3:** até o prazo, a Comissão vê só **quantas** propostas chegaram. Depois aparece o **Mapa de propostas**.
6. **Cancelar oportunidade**, com motivo, a qualquer momento antes da conclusão.

### Julgamento (Faixas 2 e 3)

1. No mapa, **Desclassificar** (com motivo) quem não atende; **Desfazer** enquanto não julgar.
2. **Julgar propostas:** o sistema aplica, nesta ordem:
   - menor preço;
   - empate ficto (até 10% acima);
   - preferência local;
   - mulher, depois mulher negra ou CadÚnico, depois MEI;
   - melhor avaliação;
   - menor % acima;
   - sorteio com semente registrada.

   Na Faixa 2, com menos de 3 propostas válidas, a Comissão precisa justificar para julgar.
3. Se houver direito de cobrir, o sistema convoca um fornecedor por vez.
4. **Homologar resultado** (Faixa 2) gera a **Ata (PDF)**. Na Faixa 3, o resultado vai para a Diretoria aprovar a ata.
5. **Anular julgamento** (com motivo) devolve as propostas para julgar de novo.

### Contratos (ordens de fornecimento)

As abas são **Em preparação**, **Aguardando assinatura**, **Em execução**, **Nota e pagamento** e **Concluídas**.

- **Preparar a ordem:** projeto/rubrica, prazo de pagamento (até 30 dias), sinal (até 30%, só eventos e alimentação), multa (2% a 10%). **Salvar** e **Emitir a ordem**.
- Ordem não assinada no prazo: **Reemitir**, **Cancelar a ordem** ou **Convocar o próximo classificado**.
- **Registrar o termo** de recebimento: definitivo, com ressalvas (correções e prazo) ou recusado, com 5 notas de 1 a 5 e evidências.
- **Contratação de urgência:** em 3 passos, o DFD resumido, o credenciado e o valor, e a justificativa (mínimo 20 caracteres). A Comissão precisa **Dar ciência** em 2 dias úteis.

### Recursos

Abas por etapa. Para recurso novo, a Comissão pode **Reconsiderar** (dá provimento) ou **Encaminhar à Diretoria** com motivação. A Comissão nunca nega o recurso sozinha.

### Sanções

- **Novo processo:** buscar o fornecedor, escolher a infração do Anexo XII, descrever os fatos e **Instaurar e notificar**. Há a opção de inativação cautelar.
- **Juntar prova** (arquivos), acompanhar a defesa e **Registrar a decisão**: arquivamento, advertência, multa, suspensão (1 a 24 meses) ou descredenciamento (1 a 3 anos).
- O sistema avisa sobre reincidência (sanção nos últimos 5 anos).

### Preços, Indicadores e Credenciados

- **Preços:** consulta por linha e descrição (menor, média, maior, teto) e **Lançar cotação** manual. O recebimento definitivo alimenta o banco sozinho.
- **Indicadores:** relatório semestral (por faixa, por projeto, credenciados ativos por linha) com **Imprimir**.
- **Credenciados:** lista interna dos habilitados vigentes, com contato, e filtros por linha, território e nome.

## Gestor da demanda

O menu tem **Contratos**, **Preços** e **Credenciados**. O gestor:

- prepara e emite ordens, registra o **Termo de Recebimento e Avaliação** e convoca o próximo classificado;
- registra a **Contratação de urgência**;
- lança preços no Banco de Preços e consulta os credenciados.

Ele não analisa requerimentos, não gerencia editais e não vê sanções.

## Financeiro

O menu tem **Contratos**, **Preços**, **Indicadores** e **Credenciados**. O Financeiro:

- **Registra a nota** fiscal quando ela chega por outro meio;
- **Registra o sinal** (parcela antecipada, até o previsto na ordem) com comprovante;
- **Registra pagamento** (Pix ou transferência) com comprovante, depois da nota fiscal. O pagamento final não pode passar do saldo (total menos sinal).

O sistema bloqueia o pagamento se o credenciamento estiver vencido ou se houver certidão vencida.

## Controle Interno

O menu tem **Contratos** e **Sanções** (só consulta), **Preços**, **Dossiês**, **Indicadores** e **Credenciados**.

- **Dossiês:** lista das contratações pagas no semestre. Cada uma tem o checklist do Anexo XIII: os itens que o sistema confere sozinho e os de confirmação manual. Clique em **Registrar revisão**; a amostra mínima é de 10%.
- Consulta tudo, sem editar.

## Diretoria

O menu tem **Atas**, **Contratos** (consulta), **Preços**, **Sanções**, **Indicadores**, **Recursos** e **Credenciados**.

- **Atas:** julgamentos da Faixa 3 aguardando aprovação. Ver o mapa e a **Minuta da ata (PDF)**. As opções são **Aprovar a ata** ou **Devolver à Comissão** (o que anula o julgamento).
- **Recursos encaminhados:** **Dar provimento** ou **Negar provimento** (5 dias úteis).
- **Sanções:** **Registrar a decisão final** sobre o recurso: manter, reformar ou anular.

## Administração (TI)

O ADMIN acessa tudo o que a Comissão acessa, mais a área **Administração**:

- **Usuários:** **Convidar pessoa da equipe** (nome, e-mail, perfis). A pessoa recebe um link por e-mail para criar a própria senha; o ADMIN nunca conhece a senha. Busca, troca de perfis e **Desativar** ou **Reativar**. O ADMIN não pode tirar o próprio perfil de administrador nem desativar a si mesmo.
- **Parâmetros:** limites das faixas, margens, prazos e metas. **Alterar** pede o novo valor e o motivo, e tudo fica no histórico.
- **Feriados:** os feriados de 2026 e 2027 já estão cadastrados. Ainda não há tela para incluir novos: peça ao desenvolvedor (é feito pela API).

Fornecedores não são criados pelo ADMIN: eles se cadastram sozinhos.

# Parte 2: Roteiro de testes

Cada teste tem um número, o perfil que executa, os passos e o resultado esperado. Siga na ordem dentro de cada seção, porque vários testes usam o que foi criado no anterior. Ao final de cada um, marque: **[ ] Passou [ ] Falhou**, com observações.

Testes marcados com **[prazo]** dependem de um prazo vencer. Combine com o desenvolvedor.

## 1. Conta e acesso (AUT)

**AUT-01: Criar conta de fornecedor.** Visitante. Clique em "Quero me credenciar", preencha nome, e-mail novo e senha válida. *Esperado:* mensagem pedindo para confirmar pelo e-mail; chega no MailHog o e-mail "Confirme seu e-mail" com o seu nome.
[ ] Passou [ ] Falhou

**AUT-02: Entrar sem confirmar o e-mail.** Tente entrar com a conta do AUT-01 antes de clicar no link. *Esperado:* bloqueia com aviso para confirmar o e-mail e link "Reenviar o e-mail de confirmação".
[ ] Passou [ ] Falhou

**AUT-03: Confirmar e-mail.** Clique no link do MailHog. *Esperado:* "E-mail confirmado"; agora o login funciona e leva para "Meu credenciamento".
[ ] Passou [ ] Falhou

**AUT-04: Link de confirmação usado duas vezes.** Abra o mesmo link de novo. *Esperado:* "Este link é inválido ou expirou".
[ ] Passou [ ] Falhou

**AUT-05: Cadastro com e-mail já existente.** Repita o cadastro com o mesmo e-mail. *Esperado:* a tela mostra a mesma mensagem de sucesso (não revela que o e-mail existe) e chega um e-mail avisando que a pessoa já tem cadastro.
[ ] Passou [ ] Falhou

**AUT-06: Senha fraca no cadastro.** Tente senhas "123", "abcdefgh" (sem número) e "12345678" (sem letra). *Esperado:* recusa com "A senha deve ter de 8 a 72 caracteres, com letras e números".
[ ] Passou [ ] Falhou

**AUT-07: E-mail inválido e campos vazios.** Envie o cadastro com e-mail "maria@" e nome vazio. *Esperado:* mensagens em cada campo; nada é criado.
[ ] Passou [ ] Falhou

**AUT-08: Senha e confirmação diferentes.** No cadastro, digite senhas diferentes nos dois campos. *Esperado:* aviso de que as senhas não conferem.
[ ] Passou [ ] Falhou

**AUT-09: Login com senha errada.** *Esperado:* "E-mail ou senha incorretos" (com o aviso de bloqueio após várias tentativas).
[ ] Passou [ ] Falhou

**AUT-10: Login com e-mail que não existe.** *Esperado:* exatamente a mesma mensagem do AUT-09.
[ ] Passou [ ] Falhou

**AUT-11: Bloqueio após 5 senhas erradas.** Erre a senha 5 vezes seguidas com um usuário de teste e tente a senha certa. *Esperado:* "Muitas tentativas sem sucesso. Aguarde 15 minutos". (Peça ao desenvolvedor para liberar depois, ou espere 15 minutos.)
[ ] Passou [ ] Falhou

**AUT-12: Bloqueio não trava o dono em outro lugar.** Logo depois do AUT-11, entre com a mesma conta **de outro computador ou celular (outra rede)**. *Esperado:* entra normalmente; o bloqueio é só para quem errou.
[ ] Passou [ ] Falhou

**AUT-13: Bloqueio vale para e-mail inexistente.** Erre 5 vezes com um e-mail que não existe e tente a 6ª. *Esperado:* a mesma mensagem de bloqueio do AUT-11.
[ ] Passou [ ] Falhou

**AUT-14: Letras maiúsculas no e-mail.** Entre digitando o e-mail em MAIÚSCULAS. *Esperado:* entra normalmente.
[ ] Passou [ ] Falhou

**AUT-15: Esqueci minha senha.** Peça nova senha para uma conta existente. *Esperado:* mensagem neutra; chega e-mail com link "nova senha"; ao definir a nova senha, a antiga deixa de funcionar e a nova funciona.
[ ] Passou [ ] Falhou

**AUT-16: Esqueci a senha com e-mail inexistente.** *Esperado:* a mesma mensagem neutra; nenhum e-mail é enviado.
[ ] Passou [ ] Falhou

**AUT-17: Link de nova senha reaproveitado.** Use de novo o link do AUT-15. *Esperado:* link inválido.
[ ] Passou [ ] Falhou

**AUT-18: Nova senha derruba sessões abertas.** Fique logado com a conta no navegador A. No navegador B, troque a senha pelo "Esqueci minha senha". Volte ao A e navegue. *Esperado:* o A é desconectado e pede login de novo.
[ ] Passou [ ] Falhou

**AUT-19: Sessão continua ao recarregar.** Logado, aperte F5 e feche/abra a aba. *Esperado:* continua logado.
[ ] Passou [ ] Falhou

**AUT-20: Sair.** Clique em Sair e depois use o botão "Voltar" do navegador. *Esperado:* não volta para a área logada; pede login.
[ ] Passou [ ] Falhou

**AUT-21: Duas abas ao mesmo tempo.** Abra o sistema logado em duas abas, deixe paradas por mais de 15 minutos e depois use as duas. *Esperado:* as duas continuam funcionando, sem "sessão expirada".
[ ] Passou [ ] Falhou

**AUT-22: Já logado abre a tela de login.** Logado, digite /entrar na barra de endereço. *Esperado:* vai para a sua área, sem ficar carregando para sempre.
[ ] Passou [ ] Falhou

**AUT-23: Mostrar senha.** Use o botão de "mostrar senha" no campo. *Esperado:* alterna entre ver e esconder.
[ ] Passou [ ] Falhou

## 2. Permissões (PER)

**PER-01: Fornecedor tenta abrir telas internas.** Como fornecedor, digite na barra: /comissao/requerimentos, /contratos, /admin, /sancoes, /credenciados, /controle, /diretoria. *Esperado:* "Acesso negado" em todas.
[ ] Passou [ ] Falhou

**PER-02: Visitante tenta abrir área logada.** Sem login, abra /fornecedor e /comissao. *Esperado:* vai para a tela de login; depois de entrar, volta para a página pedida (se o perfil puder).
[ ] Passou [ ] Falhou

**PER-03: Menu de cada perfil.** Entre com cada um dos 7 usuários de teste. *Esperado:* o menu mostra exatamente as telas da matriz de acesso da Parte 1.
[ ] Passou [ ] Falhou

**PER-04: Endereço direto por perfil.** Para cada perfil interno, digite o endereço de uma tela que ele não tem na matriz. Exemplos:
- Gestor: /sancoes e /admin;
- Financeiro: /comissao/editais;
- Controle: /contratos/urgencia;
- Diretoria: /comissao/requerimentos.

*Esperado:* "Acesso negado".
[ ] Passou [ ] Falhou

**PER-05: Consulta sem botões de edição.** Como Controle Interno e Diretoria, abra uma ordem em Contratos. *Esperado:* vê tudo, mas sem botões de emitir, salvar, registrar termo ou pagamento.
[ ] Passou [ ] Falhou

**PER-06: Fornecedor só vê o que é dele.** Com dois fornecedores (A e B), anote o endereço de um contrato ou processo do A (o número no final da URL). Entre como B e abra esse endereço. *Esperado:* "não encontrado" ou acesso negado; nunca os dados do A.
[ ] Passou [ ] Falhou

**PER-07: Desativar usuário derruba na hora.** Como ADMIN, desative um usuário interno que está logado em outro navegador. No outro navegador, clique em qualquer coisa. *Esperado:* é desconectado imediatamente; ao tentar entrar, "Sua conta está desativada".
[ ] Passou [ ] Falhou

**PER-08: Trocar perfil vale na hora.** Como ADMIN, troque o perfil de um usuário logado (ex.: de Financeiro para Gestor). No navegador dele, navegue. *Esperado:* o menu passa a ser o do novo perfil (pode ser preciso recarregar) e as telas do perfil antigo dão acesso negado.
[ ] Passou [ ] Falhou

## 3. Área pública (PUB)

**PUB-01: Página inicial.** *Esperado:* carrega, com links para Catálogo, Edital e "Quero me credenciar".
[ ] Passou [ ] Falhou

**PUB-02: Catálogo.** Abra o Catálogo e use o filtro "Aceitam pessoa física". *Esperado:* a lista filtra; voltar para "Todas" mostra tudo.
[ ] Passou [ ] Falhou

**PUB-03: Edital e anexos.** Baixe cada documento. *Esperado:* todos abrem; a Planilha de Gestão **não** aparece na lista.
[ ] Passou [ ] Falhou

**PUB-04: Página inexistente.** Abra /qualquercoisa. *Esperado:* "Página não encontrada" com caminho de volta.
[ ] Passou [ ] Falhou

**PUB-05: Credenciados não é público.** Sem login, abra /credenciados. *Esperado:* pede login (e, como fornecedor, dá acesso negado).
[ ] Passou [ ] Falhou

## 4. Requerimento de credenciamento (FOR)

Use um fornecedor novo, criado no AUT-01.

**FOR-01: Rascunho salvo.** Preencha as etapas 1 e 2, clique em **Salvar rascunho**, saia e entre de novo. *Esperado:* os dados continuam lá.
[ ] Passou [ ] Falhou

**FOR-02: CPF/CNPJ inválido.** Digite um CNPJ com dígito errado (ex.: 11.222.333/0001-82). *Esperado:* aviso de documento inválido.
[ ] Passou [ ] Falhou

**FOR-03: Autônomo menor de 18 anos.** Tipo Autônomo com data de nascimento de 16 anos atrás. *Esperado:* não deixa enviar (pendência de idade).
[ ] Passou [ ] Falhou

**FOR-04: Autônomo em linha que não aceita pessoa física.** *Esperado:* a linha não pode ser escolhida ou vira pendência.
[ ] Passou [ ] Falhou

**FOR-05: Documentos exigidos mudam com o tipo.** Compare a etapa 6 para MEI (pede CCMEI), ME/EPP (contrato social, enquadramento) e Autônomo (NIT/PIS). *Esperado:* a lista de documentos muda conforme o tipo.
[ ] Passou [ ] Falhou

**FOR-06: Upload de PDF, JPG e PNG.** *Esperado:* aceita os três; o arquivo aparece com opção de ver e excluir.
[ ] Passou [ ] Falhou

**FOR-07: Arquivo falso.** Envie o .txt renomeado para .pdf. *Esperado:* "Envie o documento em PDF, JPG ou PNG".
[ ] Passou [ ] Falhou

**FOR-08: Arquivo maior que 10 MB.** *Esperado:* recusa com mensagem de tamanho.
[ ] Passou [ ] Falhou

**FOR-09: Reenviar substitui.** Envie dois arquivos para o mesmo tipo. *Esperado:* fica só o último.
[ ] Passou [ ] Falhou

**FOR-10: Certidão sem validade, vencida ou muito longa.** Envie uma certidão sem data "válida até", depois com data passada, depois com data daqui a 2 anos. *Esperado:* recusa as três situações.
[ ] Passou [ ] Falhou

**FOR-11: Envio com pendência.** Tente enviar faltando um documento obrigatório e sem aceitar as declarações. *Esperado:* não envia e lista as pendências.
[ ] Passou [ ] Falhou

**FOR-12: Envio completo.** Preencha tudo e envie. *Esperado:* "Requerimento enviado!", com protocolo CR-AAAA-NNNNN e prazo de análise. A Comissão recebe e-mail no MailHog.
[ ] Passou [ ] Falhou

**FOR-13: Bloqueado para edição em análise.** Depois de enviar, tente alterar dados. *Esperado:* não permite edição enquanto está em análise.
[ ] Passou [ ] Falhou

**FOR-14: Equidade é facultativa.** Envie um requerimento sem preencher a etapa 5. *Esperado:* envia normalmente.
[ ] Passou [ ] Falhou

## 5. Análise pela Comissão (COM)

**COM-01: Fila de requerimentos.** Como Comissão, abra Requerimentos. *Esperado:* o requerimento do FOR-12 aparece em "Em análise", com o prazo; os contadores das abas batem.
[ ] Passou [ ] Falhou

**COM-02: Ver documentos.** Abra o requerimento e clique em **Abrir** em cada documento. *Esperado:* abrem corretamente.
[ ] Passou [ ] Falhou

**COM-03: Pedir ajuste.** Escolha **Pedir ajuste** e descreva. *Esperado:* vai para "Aguardando ajuste"; o fornecedor recebe e-mail e vê "A Comissão pediu um ajuste" com o prazo.
[ ] Passou [ ] Falhou

**COM-04: Reenvio após ajuste.** Como fornecedor, corrija e reenvie. *Esperado:* volta para "Em análise" com o mesmo protocolo e novo prazo de análise.
[ ] Passou [ ] Falhou

**COM-05: Inabilitar sem motivo.** Tente inabilitar sem escrever motivo. *Esperado:* não deixa.
[ ] Passou [ ] Falhou

**COM-06: Inabilitar.** Inabilite com motivo. *Esperado:* o fornecedor vê "Seu requerimento não foi aprovado", o motivo e o botão **Recorrer da decisão**.
[ ] Passou [ ] Falhou

**COM-07: Habilitar.** Em outro requerimento, **Habilitar**. *Esperado:* o fornecedor vê "Você está credenciado!" com validade de 24 meses e passa a aparecer em Credenciados.
[ ] Passou [ ] Falhou

**COM-08: Lista de credenciados.** Abra Credenciados e filtre por linha, território e nome. *Esperado:* aparece o habilitado, com documento mascarado (ex.: 11.222.***/****-81) e contato; dados bancários e raça/cor **não** aparecem.
[ ] Passou [ ] Falhou

**COM-09: Admin também analisa.** Repita uma decisão logado como ADMIN. *Esperado:* funciona.
[ ] Passou [ ] Falhou

## 6. Recurso de habilitação (REC)

**REC-01: Enviar recurso.** Como o fornecedor inabilitado (COM-06), **Recorrer da decisão** com razões, pedido e anexo. *Esperado:* recurso registrado; a Comissão é avisada; o requerimento fica bloqueado para edição.
[ ] Passou [ ] Falhou

**REC-02: Só um recurso por decisão.** Tente recorrer de novo. *Esperado:* não permite.
[ ] Passou [ ] Falhou

**REC-03: Comissão reconsidera.** Em Recursos, abra o recurso e **Reconsiderar**. *Esperado:* recurso provido e fornecedor habilitado por 24 meses.
[ ] Passou [ ] Falhou

**REC-04: Comissão encaminha à Diretoria.** Em outro recurso, **Encaminhar à Diretoria** com motivação. *Esperado:* sai da fila da Comissão e aparece para a Diretoria.
[ ] Passou [ ] Falhou

**REC-05: Diretoria nega.** Como Diretoria, **Negar provimento**. *Esperado:* o fornecedor é avisado e pode corrigir e reenviar o requerimento.
[ ] Passou [ ] Falhou

**REC-06: Diretoria dá provimento.** Em outro recurso, **Dar provimento**. *Esperado:* fornecedor habilitado.
[ ] Passou [ ] Falhou

**REC-07 [prazo]: Recurso fora do prazo.** Depois de 3 dias úteis da inabilitação. *Esperado:* o botão de recurso some ou recusa.
[ ] Passou [ ] Falhou

## 7. Manutenção do cadastro (MAN)

**MAN-01: Atualizar dados.** Como fornecedor habilitado, **Atualizar dados** (telefone e conta bancária) e **Salvar alterações**. *Esperado:* salva; a Comissão é avisada; com conta nova, o Financeiro também.
[ ] Passou [ ] Falhou

**MAN-02: Campos que só a Comissão muda.** Na atualização de dados, tente mudar linhas, tipo de negócio ou CNPJ. *Esperado:* não há como mudar.
[ ] Passou [ ] Falhou

**MAN-03: Trocar certidão.** Envie uma certidão nova com validade. *Esperado:* a validade é atualizada.
[ ] Passou [ ] Falhou

**MAN-04 [prazo]: Aviso RENOVAR.** Certidão a 15 dias ou menos do vencimento. *Esperado:* aviso "RENOVAR" na tela e e-mail ao fornecedor.
[ ] Passou [ ] Falhou

**MAN-05 [prazo]: Certidão vencida.** *Esperado:* o fornecedor é notificado para regularizar em 5 dias úteis; a Comissão vê em Manutenção > Certidões.
[ ] Passou [ ] Falhou

**MAN-06 [prazo]: Inativo temporário.** Sem regularizar no prazo. *Esperado:*
- o fornecedor vê "Seu cadastro está inativo temporariamente";
- some de Credenciados e de novas oportunidades;
- aparece em Manutenção > Inativos.

[ ] Passou [ ] Falhou

**MAN-07: Reativação automática.** Com o fornecedor inativo, envie a certidão em dia. *Esperado:* volta a ativo sozinho.
[ ] Passou [ ] Falhou

**MAN-08 [prazo]: Reconferência semestral.** Como Comissão, em Reconferência semestral, marque as certidões como **Conferida** e **Registrar reconferência**. *Esperado:* sai da fila; não registra se faltar data de validade.
[ ] Passou [ ] Falhou

**MAN-09 [prazo]: Renovação.** A 60 dias do fim dos 24 meses, o fornecedor **Pedir renovação**; a Comissão **Renovar**. *Esperado:* validade estendida por mais 24 meses a partir do fim atual.
[ ] Passou [ ] Falhou

**MAN-10: Devolver renovação.** A Comissão **Devolver** o pedido com o que falta. *Esperado:* o fornecedor vê o motivo; o credenciamento continua valendo até a data original.
[ ] Passou [ ] Falhou

## 8. Editais e rodízio da Faixa 1 (EDT)

Prepare **pelo menos 3 fornecedores habilitados** na mesma linha e no mesmo território.

**EDT-01: Criar rascunho.** Como Comissão, em Editais, nova oportunidade com 2 itens, valor estimado de R$ 5.000 (Faixa 1). **Salvar rascunho**. *Esperado:* aparece em "Rascunhos" como Faixa 1.
[ ] Passou [ ] Falhou

**EDT-02: Faixa calculada pelo valor.** Edite o valor para R$ 30.000 e depois para R$ 100.000. *Esperado:* muda para Faixa 2 e depois Faixa 3.
[ ] Passou [ ] Falhou

**EDT-03: Prazo mínimo na publicação.** Faixa 2 com prazo de propostas de 1 dia útil. Clique em **Publicar**. *Esperado:* recusa (mínimo de 3 dias úteis; na Faixa 3, 5).
[ ] Passou [ ] Falhou

**EDT-04: Publicar Faixa 1.** Publique a oportunidade de R$ 5.000. *Esperado:* o primeiro da fila recebe o convite (e-mail e "Chegou a sua vez no rodízio").
[ ] Passou [ ] Falhou

**EDT-05: Valor estimado escondido.** Como o fornecedor convidado, abra a oportunidade. *Esperado:* o valor estimado **não** aparece em lugar nenhum.
[ ] Passou [ ] Falhou

**EDT-06: Só quem tem a linha e o território vê.** Como fornecedor de outra linha, abra Editais em aberto. *Esperado:* a oportunidade não aparece.
[ ] Passou [ ] Falhou

**EDT-07: Recusar com motivo.** O convidado clica em **Recusar convite** e informa indisponibilidade. *Esperado:* o próximo da fila é convidado.
[ ] Passou [ ] Falhou

**EDT-08: Aceitar e enviar preço.** O novo convidado **Aceitar e enviar o preço**. *Esperado:* a Comissão vê o preço e a marca "dentro do teto" ou "acima" (quando houver referência no Banco de Preços).
[ ] Passou [ ] Falhou

**EDT-09: Recusar preço.** A Comissão clica em **Recusar e chamar o próximo**. *Esperado:* o próximo é convidado.
[ ] Passou [ ] Falhou

**EDT-10: Confirmar seleção.** Com novo aceite, **Confirmar seleção**. *Esperado:* o fornecedor vê "Você foi selecionado!" e nasce a ordem "Em preparação" em Contratos.
[ ] Passou [ ] Falhou

**EDT-11: Fila acabou.** Faça todos recusarem. *Esperado:* a tela oferece **Ampliar e convidar** para T2/T3.
[ ] Passou [ ] Falhou

**EDT-12 [prazo]: Convite sem resposta.** O convidado não responde em 2 dias úteis. *Esperado:* a vez passa ao próximo.
[ ] Passou [ ] Falhou

**EDT-13: Duas recusas sem motivo.** O mesmo fornecedor recusa sem motivo duas oportunidades seguidas. *Esperado:* vai para o fim da fila; ele e a Comissão são avisados.
[ ] Passou [ ] Falhou

**EDT-14: Cancelar oportunidade.** **Cancelar oportunidade** com motivo. *Esperado:* os fornecedores veem "Oportunidade cancelada".
[ ] Passou [ ] Falhou

**EDT-15: Exclusiva para mulheres.** Oportunidade marcada como exclusiva a negócios liderados por mulher. *Esperado:* só fornecedores que marcaram "liderado por mulher" a veem.
[ ] Passou [ ] Falhou

## 9. Propostas das Faixas 2 e 3 (PRO)

Publique uma oportunidade de Faixa 2 (ex.: R$ 30.000) com pelo menos 3 fornecedores aptos.

**PRO-01: Proposta preenchida no sistema.** Fornecedor A preenche o preço de cada item. *Esperado:* o total é calculado; validade de 30 dias e declaração obrigatórias.
[ ] Passou [ ] Falhou

**PRO-02: Modelo do Instituto.** Fornecedor B clica em **Baixar proposta (Word)**. *Esperado:* o Word vem preenchido com os dados da oportunidade e do fornecedor. Envie de volta (PDF ou foto) digitando o total.
[ ] Passou [ ] Falhou

**PRO-03: Proposta própria.** Fornecedor C envia um PDF próprio com o total. *Esperado:* aceita.
[ ] Passou [ ] Falhou

**PRO-04: Validade menor que 30 dias.** *Esperado:* recusa.
[ ] Passou [ ] Falhou

**PRO-05: Alterar e retirar.** Antes do prazo, **Alterar proposta** e depois **Retirar proposta**. *Esperado:* as duas ações funcionam; depois de retirar, pode enviar de novo.
[ ] Passou [ ] Falhou

**PRO-06: Sigilo até o prazo.** Como Comissão, abra a oportunidade antes do prazo. *Esperado:* vê só a **quantidade** de propostas, sem valores nem nomes.
[ ] Passou [ ] Falhou

**PRO-07 [prazo]: Depois do prazo.** *Esperado:* o fornecedor não consegue mais enviar ou alterar; a Comissão passa a ver o Mapa de propostas ordenado pelo total.
[ ] Passou [ ] Falhou

**PRO-08: Não posso atender.** Um fornecedor convidado clica em **Não posso atender**. *Esperado:* a oportunidade vai para "Encerrados e respondidos".
[ ] Passou [ ] Falhou

**PRO-09: Faixa 3 com requerimento em análise.** Fornecedor ainda não habilitado (requerimento enviado) numa Faixa 3. *Esperado:* consegue enviar proposta. Se não for habilitado até o julgamento, é desclassificado automaticamente, com o motivo.
[ ] Passou [ ] Falhou

## 10. Julgamento (JUL)

**JUL-01: Desclassificar e desfazer.** Antes de julgar, **Desclassificar** uma proposta (com motivo) e depois **Desfazer**. *Esperado:* as duas funcionam.
[ ] Passou [ ] Falhou

**JUL-02: Menos de 3 propostas válidas (Faixa 2).** Com só 2 válidas, clique em **Julgar propostas**. *Esperado:* exige justificativa ou cancelamento; a justificativa vai para a ata.
[ ] Passou [ ] Falhou

**JUL-03: Menor preço vence.** Três propostas bem diferentes (ex.: 20.000, 25.000, 28.000) e sem critérios de preferência. *Esperado:* vence a de 20.000.
[ ] Passou [ ] Falhou

**JUL-04: Empate ficto com preferência a mulher.** A (sem preferência) com 20.000 e B (liderado por mulher) com 21.000, que fica dentro dos 10%. *Esperado:* B fica em 1º e é convocado a cobrir.
[ ] Passou [ ] Falhou

**JUL-05: Cobrir com valor menor.** Como B, **Enviar novo valor** de 19.900. *Esperado:* B vence com 19.900.
[ ] Passou [ ] Falhou

**JUL-06: Cobrir com valor igual.** Repita o cenário e tente cobrir com 20.000 (igual ao menor). *Esperado:* recusa; o valor precisa ser **menor**.
[ ] Passou [ ] Falhou

**JUL-07: Não quero cobrir.** Repita e clique em **Não quero cobrir**. *Esperado:* convoca o próximo finalista ou, se não houver, vence o menor preço.
[ ] Passou [ ] Falhou

**JUL-08 [prazo]: Não respondeu à convocação.** Passa 1 dia útil sem resposta. *Esperado:* a convocação passa ao próximo.
[ ] Passou [ ] Falhou

**JUL-09: Preferência local.** Duas propostas dentro da margem, uma com sede em Mesquita (T1). *Esperado:* a de T1 tem preferência.
[ ] Passou [ ] Falhou

**JUL-10: Sigilo para o convocado.** Como o convocado a cobrir. *Esperado:* vê o valor a cobrir, mas não o nome dos concorrentes. Os outros fornecedores não veem nada até o resultado.
[ ] Passou [ ] Falhou

**JUL-11: Homologar (Faixa 2).** **Homologar resultado**. *Esperado:* gera a **Ata (PDF)**; o vencedor vê "Sua proposta foi a selecionada!"; nasce a ordem em Contratos.
[ ] Passou [ ] Falhou

**JUL-12: Conteúdo da ata.** Abra o PDF da ata. *Esperado:* tem o mapa, os critérios aplicados e a semente do sorteio (se houve). **Não** tem raça ou CadÚnico de ninguém.
[ ] Passou [ ] Falhou

**JUL-13: Minuta antes de homologar.** Antes de homologar, abra a ata. *Esperado:* sai como "Minuta da ata".
[ ] Passou [ ] Falhou

**JUL-14: Anular julgamento.** Em um julgamento em curso, **Anular julgamento** com motivo. *Esperado:* as propostas voltam; é possível julgar de novo; o histórico mostra a anulação.
[ ] Passou [ ] Falhou

**JUL-15: O que o perdedor vê.** Como fornecedor que perdeu. *Esperado:* vê o valor vencedor, um motivo genérico e a própria posição, sem nomes de concorrentes.
[ ] Passou [ ] Falhou

## 11. Diretoria e Faixa 3 (DIR)

**DIR-01: Ata da Faixa 3 vai para a Diretoria.** Julgue uma Faixa 3 como Comissão. *Esperado:* não há botão de homologar; aparece em Atas para a Diretoria.
[ ] Passou [ ] Falhou

**DIR-02: Aprovar a ata.** Como Diretoria, veja o mapa e a minuta e clique em **Aprovar a ata**. *Esperado:* homologado; a ata final é gerada; nasce a ordem.
[ ] Passou [ ] Falhou

**DIR-03: Devolver à Comissão.** Em outra Faixa 3, **Devolver à Comissão**. *Esperado:* o julgamento é anulado e volta para a Comissão.
[ ] Passou [ ] Falhou

## 12. Recurso contra o resultado (RJU)

**RJU-01: Recorrer do resultado.** Como fornecedor que perdeu (JUL-11), **Recorrer do resultado** com razões e anexo. *Esperado:* aparece em Recursos, com a etapa "julgamento".
[ ] Passou [ ] Falhou

**RJU-02: Ordem travada durante o recurso.** Com o recurso em análise, a Comissão tenta emitir a ordem daquela oportunidade. *Esperado:* bloqueia.
[ ] Passou [ ] Falhou

**RJU-03: Recurso provido.** Comissão **Reconsiderar** (ou Diretoria **Dar provimento**). *Esperado:* o julgamento é anulado, a ordem não executada é cancelada e a oportunidade volta para "Em julgamento".
[ ] Passou [ ] Falhou

**RJU-04: Vencedor não recorre.** Como o vencedor. *Esperado:* não há botão de recurso.
[ ] Passou [ ] Falhou

## 13. Ordem de fornecimento e execução (ORD)

**ORD-01: Preparar e emitir.** Como Comissão ou Gestor, abra a ordem "Em preparação", preencha projeto/rubrica, prazo de pagamento, sinal e multa, **Salvar** e **Emitir a ordem**. *Esperado:* vai para "Aguardando assinatura" (Faixas 2 e 3) ou já assinada (Faixa 1).
[ ] Passou [ ] Falhou

**ORD-02: Limites da ordem.** Tente prazo de pagamento acima de 30 dias, sinal acima de 30% e multa fora de 2% a 10%. *Esperado:* recusa cada um.
[ ] Passou [ ] Falhou

**ORD-03: Minuta e PDF.** Baixe o PDF antes e depois de emitir. *Esperado:* antes, "Minuta (PDF)"; depois, "Ordem (PDF)" com número.
[ ] Passou [ ] Falhou

**ORD-04: Assinar sem marcar a declaração.** Como fornecedor, em Meus contratos, **Assinar eletronicamente** sem marcar a confirmação. *Esperado:* não deixa.
[ ] Passou [ ] Falhou

**ORD-05: Assinar.** Marque e assine. *Esperado:* status "Ordem assinada, hora de executar"; a equipe vê data, hora e IP da assinatura.
[ ] Passou [ ] Falhou

**ORD-06 [prazo]: Não assinou em 2 dias úteis.** *Esperado:* a ordem fica "não assinada" e a equipe é avisada; aparecem **Reemitir**, **Cancelar a ordem** e **Convocar o próximo classificado**.
[ ] Passou [ ] Falhou

**ORD-07: Convocar o próximo classificado.** *Esperado:* a ordem atual é cancelada e nasce outra para o 2º colocado, com o preço dele e as mesmas condições.
[ ] Passou [ ] Falhou

**ORD-08: Termo com ressalvas.** Como Gestor, **Registrar o termo** "com ressalvas", com notas, correções, prazo e evidência. *Esperado:* o fornecedor vê "Recebido com ressalvas"; depois é possível registrar novo termo.
[ ] Passou [ ] Falhou

**ORD-09: Termo definitivo.** Registre o termo definitivo com as 5 notas. *Esperado:*
- a média é calculada;
- o fornecedor vê as notas e clica em **Dar ciência do termo**;
- os preços entram no Banco de Preços.

[ ] Passou [ ] Falhou

**ORD-10: Termo recusado.** Em outra ordem, registre "recusado". *Esperado:* o fornecedor vê "Objeto recusado".
[ ] Passou [ ] Falhou

**ORD-11: Nota fiscal antes do recebimento.** O fornecedor tenta enviar a nota antes do termo definitivo. *Esperado:* não permite.
[ ] Passou [ ] Falhou

**ORD-12: Enviar nota fiscal.** Depois do termo definitivo, **Enviar nota fiscal** (número, data, valor, arquivo). *Esperado:* status "Nota fiscal recebida".
[ ] Passou [ ] Falhou

**ORD-13: Sinal.** Numa ordem com sinal previsto (ex.: 20%), assinada, o Financeiro **Registrar o sinal** acima do previsto e depois dentro do previsto. *Esperado:* recusa o primeiro e aceita o segundo, com comprovante.
[ ] Passou [ ] Falhou

**ORD-14: Pagamento final.** O Financeiro **Registrar pagamento** com valor maior que o saldo e depois com o saldo. *Esperado:* recusa o primeiro e aceita o segundo; status "Pago"; o fornecedor é avisado.
[ ] Passou [ ] Falhou

**ORD-15: Pagamento com certidão vencida.** Com o fornecedor com certidão vencida, tente pagar. *Esperado:* bloqueia e orienta a regularização.
[ ] Passou [ ] Falhou

**ORD-16: Comprovante para o fornecedor.** Como fornecedor, em Meus contratos. *Esperado:* consegue baixar a própria nota e o comprovante de pagamento.
[ ] Passou [ ] Falhou

## 14. Contratação de urgência (URG)

**URG-01: Justificativa curta.** Como Gestor, em Contratos > Contratação de urgência, preencha o DFD, escolha o credenciado e escreva justificativa com menos de 20 caracteres. *Esperado:* não deixa.
[ ] Passou [ ] Falhou

**URG-02: Só credenciado ativo da linha.** *Esperado:* a lista de credenciados só traz ativos da linha escolhida.
[ ] Passou [ ] Falhou

**URG-03: Contratar com urgência.** Complete e clique em **Contratar com urgência**. *Esperado:* nasce a oportunidade numerada e a ordem "Em preparação"; a Comissão é avisada; a assinatura é obrigatória mesmo em valor de Faixa 1.
[ ] Passou [ ] Falhou

**URG-04: Ciência da Comissão.** Como Comissão, veja a pendência no alto de Contratos e **Dar ciência**. *Esperado:* a pendência some.
[ ] Passou [ ] Falhou

## 15. Sanções (SAN)

**SAN-01: Instaurar.** Como Comissão, em Sanções > novo, busque o fornecedor, escolha a infração, descreva e **Instaurar e notificar**. *Esperado:* o fornecedor recebe e-mail e vê o processo com prazo de defesa de 5 dias úteis.
[ ] Passou [ ] Falhou

**SAN-02: Juntar prova.** Anexe um arquivo de prova. *Esperado:* aparece no processo para a equipe.
[ ] Passou [ ] Falhou

**SAN-03: Defesa.** Como fornecedor, **Enviar defesa** com anexo. *Esperado:* o processo vai para "Em decisão pela Comissão".
[ ] Passou [ ] Falhou

**SAN-04: Multa não prevista.** A Comissão tenta aplicar multa num processo sem ordem com multa prevista. *Esperado:* não permite.
[ ] Passou [ ] Falhou

**SAN-05: Suspensão.** **Registrar a decisão** de suspensão de 3 meses. *Esperado:* "Decidido, prazo de recurso"; o fornecedor pode **Enviar recurso**.
[ ] Passou [ ] Falhou

**SAN-06: Recurso e decisão final.** O fornecedor recorre; a Diretoria **Registrar a decisão final** mantendo a suspensão. *Esperado:*
- processo "Encerrado";
- o fornecedor some de Credenciados e de novas oportunidades;
- não pode receber ordem nem pagamento.

[ ] Passou [ ] Falhou

**SAN-07: Anulação pela Diretoria.** Em outro processo, a Diretoria anula. *Esperado:* sem sanção; nada é bloqueado.
[ ] Passou [ ] Falhou

**SAN-08: Inativação cautelar.** Instaure marcando inativação cautelar. *Esperado:* o fornecedor fica bloqueado desde a abertura; se a decisão final não bloquear, o bloqueio cai.
[ ] Passou [ ] Falhou

**SAN-09: Reincidência.** Abra um segundo processo para o mesmo fornecedor. *Esperado:* a tela sinaliza "Outros processos deste fornecedor" e a reincidência.
[ ] Passou [ ] Falhou

**SAN-10: Fornecedor vê só os seus.** *Esperado:* o fornecedor vê os próprios processos, sem anotações internas da Comissão.
[ ] Passou [ ] Falhou

**SAN-11 [prazo]: Sem defesa e sem recurso.** *Esperado:* sem defesa no prazo, segue para decisão; sem recurso no prazo, vale a decisão da Comissão.
[ ] Passou [ ] Falhou

## 16. Banco de Preços, Dossiês e Indicadores (CTL)

**CTL-01: Consulta de preços.** Em Preços, busque por linha e descrição. *Esperado:* mostra quantidade, menor, média, maior e teto (média + 10%).
[ ] Passou [ ] Falhou

**CTL-02: Lançar cotação.** **Lançar cotação** manual. *Esperado:* entra na consulta; não existe a opção "Contratação" no lançamento manual.
[ ] Passou [ ] Falhou

**CTL-03: Preço do recebimento.** Depois do ORD-09, consulte os itens daquela ordem. *Esperado:* aparecem com fonte "Contratação" e o número da ordem.
[ ] Passou [ ] Falhou

**CTL-04: Dossiê.** Como Controle Interno, abra Dossiês e uma contratação paga. *Esperado:* checklist do Anexo XIII com os itens automáticos marcados e os manuais para confirmar.
[ ] Passou [ ] Falhou

**CTL-05: Registrar revisão.** **Registrar revisão**. *Esperado:* fica registrado, inclusive se o dossiê estava completo naquele momento.
[ ] Passou [ ] Falhou

**CTL-06: Indicadores.** Abra Indicadores, mude o período e **Imprimir**. *Esperado:* números por faixa, por projeto e credenciados ativos por linha (com alerta abaixo de 3 em T1); a impressão sai legível.
[ ] Passou [ ] Falhou

## 17. Administração (ADM)

**ADM-01: Convidar usuário.** Como ADMIN, em Usuários, **Convidar pessoa da equipe** com perfil Comissão. *Esperado:* a pessoa recebe e-mail com link; ao criar a senha, consegue entrar como Comissão.
[ ] Passou [ ] Falhou

**ADM-02: Convite com e-mail já usado.** *Esperado:* "Já existe um usuário com este e-mail".
[ ] Passou [ ] Falhou

**ADM-03: Convite sem perfil.** *Esperado:* "Selecione ao menos um perfil".
[ ] Passou [ ] Falhou

**ADM-04: Proteção contra auto-bloqueio.** O ADMIN tenta tirar o próprio perfil de administrador ou se desativar. *Esperado:* não permite.
[ ] Passou [ ] Falhou

**ADM-05: Alteração simultânea.** Abra o mesmo usuário em duas abas, salve na primeira e depois na segunda. *Esperado:* a segunda avisa que foi alterado por outra pessoa.
[ ] Passou [ ] Falhou

**ADM-06: Alterar parâmetro.** Em Parâmetros, **Alterar** um prazo com motivo. *Esperado:* o novo valor vale e aparece no histórico com o motivo.
[ ] Passou [ ] Falhou

**ADM-07: Parâmetro sem motivo ou inválido.** Tente salvar sem motivo e com valor inválido (ex.: texto num campo numérico). *Esperado:* recusa.
[ ] Passou [ ] Falhou

**ADM-08: Prazos pulam feriados e fins de semana.** Em qualquer tela com prazo em dias úteis (ex.: pedido de ajuste, 5 dias úteis), confira a data no calendário. *Esperado:* a contagem não inclui sábados, domingos e feriados (ex.: 25/09, aniversário de Mesquita), e não vence em dia não útil.
[ ] Passou [ ] Falhou

**ADM-09: Só o ADMIN altera parâmetros.** Como Comissão, digite /admin/parametros. *Esperado:* "Acesso negado".
[ ] Passou [ ] Falhou

## 18. Usabilidade, celular e navegadores (USA)

**USA-01: Celular.** Repita AUT-01, FOR-12 e PRO-01 num celular. *Esperado:* tudo cabe na tela, sem rolagem lateral; botões fáceis de tocar.
[ ] Passou [ ] Falhou

**USA-02: Navegadores.** Faça login e um fluxo curto no Chrome, no Edge e no Firefox (e no Safari, se tiver iPhone). *Esperado:* funciona igual em todos.
[ ] Passou [ ] Falhou

**USA-03: Teclado.** Faça o login e o cadastro usando só o teclado (Tab, Enter). *Esperado:* dá para chegar a todos os campos e botões, com o foco visível.
[ ] Passou [ ] Falhou

**USA-04: Zoom de 200%.** Aumente o zoom do navegador para 200%. *Esperado:* o texto continua legível e nada se sobrepõe.
[ ] Passou [ ] Falhou

**USA-05: Mensagens claras.** Durante todos os testes, anote qualquer mensagem confusa, técnica ou em inglês. *Esperado:* todas as mensagens em português simples.
[ ] Passou [ ] Falhou

**USA-06: Botão duplo.** Clique duas vezes rápido em botões de envio (enviar requerimento, proposta, emitir ordem). *Esperado:* não cria registro duplicado.
[ ] Passou [ ] Falhou

**USA-07: Internet lenta.** No Chrome, ative a limitação de rede (F12 > Network > Slow 3G) e envie um formulário. *Esperado:* o botão mostra que está enviando e não trava a tela.
[ ] Passou [ ] Falhou

**USA-08: Datas e valores.** Confira datas (dd/mm/aaaa) e valores (R$ 1.234,56) em todas as telas. *Esperado:* sempre no formato brasileiro.
[ ] Passou [ ] Falhou

# Resumo para entregar

Ao terminar, entregue:

1. este roteiro com todos os testes marcados;
2. a lista de falhas no formato da seção "Como registrar um problema", da mais grave para a mais leve;
3. as sugestões de melhoria de texto ou de tela que anotou no USA-05.
