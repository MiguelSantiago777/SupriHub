-- =====================================================================
-- Fase 1 — Certidões e manutenção do cadastro (Política art. 21, § 2º, 22 e 23;
-- Manual item 3, passo 8; Planilha, aba Cadastro, colunas "Venc. ..." e "Alerta certidões").
-- =====================================================================

INSERT INTO parameter (key, value, value_type, category, description, source) VALUES
('renewal_notice_days', '60', 'INTEGER', 'CREDENCIAMENTO',
 'Antecedência do aviso e da abertura da renovação do credenciamento (dias)', 'Política art. 22; Manual item 3, passo 8');

-- Validade de cada certidão (federal, FGTS, CNDT e municipal). Informada pelo fornecedor no
-- envio e conferida pela Comissão nos sítios oficiais (Manual item 3, passo 3).
ALTER TABLE supplier_document ADD COLUMN valid_until   DATE;
ALTER TABLE supplier_document ADD COLUMN verified_at   TIMESTAMPTZ;
ALTER TABLE supplier_document ADD COLUMN verified_by   BIGINT REFERENCES app_user (id);
-- Aviso "RENOVAR" já enviado para esta certidão (um por arquivo; trocar a certidão zera).
ALTER TABLE supplier_document ADD COLUMN alert_sent_on DATE;
CREATE INDEX ix_supplier_document_valid_until ON supplier_document (valid_until) WHERE valid_until IS NOT NULL;

-- Manutenção do credenciado
-- Primeiro dia com certidão vencida (ou faltando) sem regularização.
ALTER TABLE supplier_application ADD COLUMN irregular_since              DATE;
-- Prazo da notificação para apresentar a certidão (5 d.u., Política art. 21, § 2º).
ALTER TABLE supplier_application ADD COLUMN regularization_due           DATE;
-- Inativação temporária (Política art. 22): fora das oportunidades até regularizar.
ALTER TABLE supplier_application ADD COLUMN inactive_since               DATE;
-- Alerta de descredenciamento enviado à Comissão (30 dias irregular, Política art. 23 — D-014).
ALTER TABLE supplier_application ADD COLUMN deregistration_alerted_at    TIMESTAMPTZ;
-- Reconferência semestral das certidões pela Comissão (Manual item 3, passo 8).
ALTER TABLE supplier_application ADD COLUMN last_recheck_on              DATE;
ALTER TABLE supplier_application ADD COLUMN last_recheck_by              BIGINT REFERENCES app_user (id);
-- Renovação aos 24 meses (Política art. 22).
ALTER TABLE supplier_application ADD COLUMN renewal_notice_sent_at       TIMESTAMPTZ;
ALTER TABLE supplier_application ADD COLUMN renewal_requested_at         TIMESTAMPTZ;
ALTER TABLE supplier_application ADD COLUMN renewal_return_message       VARCHAR(3000);

-- Habilitados antes desta versão: a reconferência conta a partir da habilitação.
UPDATE supplier_application SET last_recheck_on = CAST(decided_at AT TIME ZONE 'America/Sao_Paulo' AS DATE)
 WHERE status = 'APPROVED' AND decided_at IS NOT NULL;

CREATE INDEX ix_supplier_application_maintenance
    ON supplier_application (inactive_since, irregular_since) WHERE status = 'APPROVED';
