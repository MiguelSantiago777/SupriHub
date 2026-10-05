-- Parâmetros do Contrata + NATA (aba "Parâmetros" da Planilha de Gestão + prazos do Edital/Política).
-- Editáveis pelo ADMIN, com histórico em parameter_history. Nunca fixar estes valores no código.
INSERT INTO parameter (key, value, value_type, category, description, source) VALUES
-- Faixas de valor
('reference_year',                     '2026',      'INTEGER', 'FAIXAS',   'Ano de referência dos limites das faixas', 'Ano do decreto federal de atualização'),
('update_decree',                      'Decreto nº 12.807/2025', 'TEXT', 'FAIXAS', 'Decreto federal de atualização dos valores da Lei nº 14.133/2021', 'Publicado em 29/12/2025; vigente a partir de 01/01/2026'),
('band1_upper_limit',                  '13098.41',  'DECIMAL', 'FAIXAS',   'Limite superior da Faixa 1 (R$)', 'Art. 95, § 2º, Lei nº 14.133/2021; Política art. 13'),
('band2_upper_limit',                  '65492.11',  'DECIMAL', 'FAIXAS',   'Limite superior da Faixa 2 (R$)', 'Art. 75, II, Lei nº 14.133/2021; Política art. 13'),
-- Tratamento favorecido / julgamento
('fictitious_tie_margin',              '0.10',      'DECIMAL', 'JULGAMENTO', 'Margem do empate ficto (pequenos negócios)', 'Edital 8.2; art. 44, § 1º, LC nº 123/2006'),
('local_preference_margin',            '0.10',      'DECIMAL', 'JULGAMENTO', 'Margem de preferência local/regional', 'Edital 8.3; art. 48, § 3º, LC nº 123/2006'),
('local_preference_for_all',           'false',     'BOOLEAN', 'JULGAMENTO', 'Aplicar preferência local também a quem não é pequeno negócio', 'DECISOES.md — padrão da Planilha/Manual'),
('cover_requires_strictly_lower',      'true',      'BOOLEAN', 'JULGAMENTO', 'Direito de cobrir exige valor estritamente inferior ao menor preço', 'Edital 8.2 ("valor inferior à primeira colocada")'),
('cover_deadline_business_days',       '1',         'INTEGER', 'JULGAMENTO', 'Prazo para exercer o direito de cobrir (dias úteis)', 'Edital 8.2; Manual item 5'),
('band2_min_valid_proposals',          '3',         'INTEGER', 'JULGAMENTO', 'Mínimo de propostas válidas na Faixa 2', 'Edital 7.1; Política art. 13'),
('exclusivity_min_small_businesses',   '3',         'INTEGER', 'JULGAMENTO', 'Mínimo de pequenos negócios na linha/território para exclusividade', 'Edital 8.1; Política art. 14, I'),
('women_exclusive_min_suppliers',      '3',         'INTEGER', 'JULGAMENTO', 'Mínimo de credenciadas aptas para chamada exclusiva a mulheres', 'Edital 8.5; Política art. 14, V'),
('price_bank_ceiling_margin',          '0.10',      'DECIMAL', 'JULGAMENTO', 'Teto sobre a média do Banco de Preços (Faixa 1)', 'Manual 4.1 — média + margem'),
-- Propostas
('band1_acceptance_business_days',     '2',         'INTEGER', 'PROPOSTAS', 'Prazo de aceite no rodízio da Faixa 1 (dias úteis)', 'Edital 7.1'),
('band2_min_proposal_business_days',   '3',         'INTEGER', 'PROPOSTAS', 'Prazo mínimo para propostas na Faixa 2 (dias úteis)', 'Edital 7.1'),
('band3_min_proposal_business_days',   '5',         'INTEGER', 'PROPOSTAS', 'Prazo mínimo para propostas na Faixa 3 (dias úteis)', 'Edital 7.1'),
('proposal_validity_days',             '30',        'INTEGER', 'PROPOSTAS', 'Validade mínima da proposta (dias)', 'Edital 7.4; Anexo VII'),
('service_order_signature_business_days','2',       'INTEGER', 'PROPOSTAS', 'Prazo para assinar a Ordem de Fornecimento (dias úteis)', 'Edital 9.1'),
('upfront_payment_max_share',          '0.30',      'DECIMAL', 'PROPOSTAS', 'Sinal máximo (eventos e alimentação)', 'Edital 10.4'),
-- Credenciamento
('accreditation_validity_months',      '24',        'INTEGER', 'CREDENCIAMENTO', 'Validade do credenciamento (meses)', 'Política art. 22; Edital 6.1'),
('certificate_alert_days',             '15',        'INTEGER', 'CREDENCIAMENTO', 'Antecedência do alerta de certidões (dias)', 'Planilha — aviso "RENOVAR"'),
('certificate_recheck_months',         '6',         'INTEGER', 'CREDENCIAMENTO', 'Periodicidade da reconferência de certidões (meses)', 'Manual item 3, passo 8'),
('certificate_regularization_business_days','5',    'INTEGER', 'CREDENCIAMENTO', 'Prazo para apresentar certidão vencida antes da inativação (dias úteis)', 'Política art. 21, § 2º'),
('deregistration_after_irregular_days','30',        'INTEGER', 'CREDENCIAMENTO', 'Dias de irregularidade não sanada para alerta de descredenciamento', 'Política art. 23'),
('analysis_business_days',             '10',        'INTEGER', 'CREDENCIAMENTO', 'Prazo de análise do requerimento completo (dias úteis)', 'Edital 4.3'),
('diligence_business_days',            '5',         'INTEGER', 'CREDENCIAMENTO', 'Prazo para atender diligência (dias úteis)', 'Edital 4.3'),
('fiscal_regularization_business_days','5',         'INTEGER', 'CREDENCIAMENTO', 'Prazo de regularização fiscal de pequenos negócios (dias úteis)', 'Edital 4.4'),
('fiscal_regularization_extension_business_days','5','INTEGER','CREDENCIAMENTO', 'Prorrogação da regularização fiscal (dias úteis)', 'Edital 4.4'),
('appeal_filing_business_days',        '3',         'INTEGER', 'CREDENCIAMENTO', 'Prazo para recorrer (dias úteis)', 'Edital 5.2'),
('appeal_reconsideration_business_days','3',        'INTEGER', 'CREDENCIAMENTO', 'Prazo para a Comissão reconsiderar (dias úteis)', 'Edital 5.2'),
('board_appeal_decision_business_days','5',         'INTEGER', 'CREDENCIAMENTO', 'Prazo para a Diretoria decidir o recurso (dias úteis)', 'Edital 5.2'),
('registration_update_business_days',  '5',         'INTEGER', 'CREDENCIAMENTO', 'Prazo para comunicar alterações cadastrais (dias úteis)', 'Edital 6.3'),
('upload_max_size_mb',                 '10',        'INTEGER', 'CREDENCIAMENTO', 'Tamanho máximo de cada documento enviado (MB)', 'Requisito do sistema'),
-- Calendário
('optional_points_are_non_business',   'true',      'BOOLEAN', 'CALENDARIO', 'Pontos facultativos (Carnaval, Corpus Christi etc.) contam como dias não úteis', 'DECISOES.md'),
-- Metas anuais (Política art. 15; Manual item 7)
('goal_small_business_share',          '0.70',      'DECIMAL', 'METAS',    'Meta: % do valor com pequenos negócios', 'Política art. 15'),
('goal_women_share',                   '0.30',      'DECIMAL', 'METAS',    'Meta: % do valor com negócios liderados por mulheres', 'Política art. 15'),
('goal_t1_share',                      '0.50',      'DECIMAL', 'METAS',    'Meta: % do valor com fornecedores T1', 'Política art. 15'),
('goal_min_avg_rating',                '4.0',       'DECIMAL', 'METAS',    'Meta: avaliação média mínima', 'Manual item 7'),
('goal_max_urgency_share',             '0.05',      'DECIMAL', 'METAS',    'Meta: % máximo de contratações de urgência', 'Manual item 7');
