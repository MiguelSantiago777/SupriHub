-- =====================================================================
-- Fase 1 — Recurso da inabilitação (Edital 5.2; Anexo X) e lista pública
-- de credenciados (Política art. 27; Edital 4.6).
-- =====================================================================

-- Prazo para recorrer, fixado na inabilitação (3 d.u. da comunicação).
ALTER TABLE supplier_application ADD COLUMN appeal_deadline DATE;

-- Anexo II, item 5: "Autorizo a divulgação, na lista pública de credenciados, da indicação
-- de 'negócio liderado por mulher'". Sem autorização, a indicação não é publicada.
ALTER TABLE supplier_application ADD COLUMN publish_woman_led BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_supplier_application_public
    ON supplier_application (accreditation_valid_until) WHERE status = 'APPROVED';

CREATE TABLE application_appeal (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id         BIGINT        NOT NULL REFERENCES supplier_application (id) ON DELETE CASCADE,
    -- FILED (na Comissão) | FORWARDED (na Diretoria) | GRANTED (provido) | DENIED (negado)
    status                 VARCHAR(20)   NOT NULL DEFAULT 'FILED',
    reasons                VARCHAR(5000) NOT NULL, -- razões do recurso (Anexo X)
    request                VARCHAR(2000) NOT NULL, -- pedido
    attachment_filename    VARCHAR(200),
    attachment_type        VARCHAR(100),
    attachment_size        BIGINT,
    attachment_sha256      VARCHAR(64),
    attachment_key         VARCHAR(200),
    filed_at               TIMESTAMPTZ   NOT NULL,
    filed_by               BIGINT        NOT NULL REFERENCES app_user (id),
    reconsideration_due    DATE          NOT NULL, -- Comissão: 3 d.u.
    commission_reason      VARCHAR(3000),
    commission_decided_at  TIMESTAMPTZ,
    commission_decided_by  BIGINT REFERENCES app_user (id),
    board_due              DATE,                   -- Diretoria: 5 d.u. do encaminhamento
    board_reason           VARCHAR(3000),
    board_decided_at       TIMESTAMPTZ,
    board_decided_by       BIGINT REFERENCES app_user (id),
    version                BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_application_appeal_status ON application_appeal (status, filed_at);
CREATE INDEX ix_application_appeal_application ON application_appeal (application_id, filed_at DESC);
-- No máximo um recurso em andamento por requerimento.
CREATE UNIQUE INDEX ux_application_appeal_open
    ON application_appeal (application_id) WHERE status IN ('FILED', 'FORWARDED');
