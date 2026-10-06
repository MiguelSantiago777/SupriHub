-- =====================================================================
-- Fase 3 — parte A: da seleção ao pagamento (Edital 9 e 10; Manual 4.2, passos 7 a 11).
-- Ordem de Fornecimento/Serviço (Anexo VIII), Termo de Recebimento e Avaliação (Anexo IX),
-- nota fiscal/RPA, pagamento e Banco de Preços.
-- =====================================================================

INSERT INTO parameter (key, value, value_type, category, description, source) VALUES
('payment_deadline_days',              '30',   'INTEGER', 'CONTRATACAO', 'Prazo máximo de pagamento após o recebimento definitivo (dias corridos)', 'Edital 10.1'),
('low_rating_threshold',               '3',    'DECIMAL', 'CONTRATACAO', 'Média do Termo de Recebimento abaixo da qual a avaliação conta como insuficiente', 'Edital 9.5; Anexo IX'),
('low_rating_occurrences',             '2',    'INTEGER', 'CONTRATACAO', 'Avaliações insuficientes que, no período, ensejam processo de suspensão', 'Edital 9.5'),
('low_rating_window_months',           '12',   'INTEGER', 'CONTRATACAO', 'Período considerado para as avaliações insuficientes (meses)', 'Edital 9.5'),
('rating_window_months',               '24',   'INTEGER', 'JULGAMENTO',  'Período das avaliações usadas no desempate (meses)', 'Edital 8.4 — melhor avaliação; DECISOES D-081');

CREATE SEQUENCE service_order_number_seq;

CREATE TABLE service_order (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number               VARCHAR(20)    UNIQUE,       -- OF-AAAA-NNNN, atribuído na emissão
    tender_id            BIGINT         NOT NULL UNIQUE REFERENCES tender (id),
    application_id       BIGINT         NOT NULL REFERENCES supplier_application (id),
    proposal_id          BIGINT         REFERENCES proposal (id),
    -- DRAFT | ISSUED | EXPIRED | SIGNED | RECEIVED_WITH_RESERVATIONS | RECEIVED | REJECTED
    -- | INVOICED | PAID | CANCELLED
    status               VARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
    kind                 VARCHAR(10)    NOT NULL DEFAULT 'SUPPLY',   -- SUPPLY (OF) | SERVICE (OS)
    total_value          NUMERIC(14, 2) NOT NULL CHECK (total_value > 0),
    project              VARCHAR(300),   -- projeto / parceria / rubrica
    object_description   VARCHAR(3000)  NOT NULL,
    schedule             VARCHAR(300),
    location             VARCHAR(300),
    payment_days         INT            NOT NULL,
    upfront_value        NUMERIC(14, 2),
    penalty_pct          NUMERIC(5, 2),
    notes                VARCHAR(2000),
    -- Faixa 1: o aceite eletrônico do rodízio substitui a assinatura (Edital 9.2)
    acceptance_based     BOOLEAN        NOT NULL DEFAULT FALSE,
    issued_at            TIMESTAMPTZ,
    issued_by            BIGINT REFERENCES app_user (id),
    signature_due_date   DATE,
    signed_at            TIMESTAMPTZ,
    signed_via           VARCHAR(20),    -- ELECTRONIC | ACCEPTANCE
    signed_by            BIGINT REFERENCES app_user (id),
    signature_ip         VARCHAR(64),
    received_at          TIMESTAMPTZ,    -- recebimento definitivo
    payment_due_date     DATE,
    rating               NUMERIC(3, 2),  -- média do termo final (definitivo ou recusado)
    rated_at             TIMESTAMPTZ,
    invoice_number       VARCHAR(60),
    invoice_date         DATE,
    invoice_value        NUMERIC(14, 2),
    invoiced_at          TIMESTAMPTZ,
    paid_on              DATE,
    paid_value           NUMERIC(14, 2),
    payment_method       VARCHAR(20),    -- PIX | TRANSFER
    paid_recorded_by     BIGINT REFERENCES app_user (id),
    cancel_reason        VARCHAR(1000),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version              BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_service_order_status ON service_order (status);
CREATE INDEX ix_service_order_application ON service_order (application_id, rated_at);
CREATE INDEX ix_service_order_signature ON service_order (signature_due_date) WHERE status = 'ISSUED';

CREATE TABLE service_order_item (
    service_order_id BIGINT         NOT NULL REFERENCES service_order (id) ON DELETE CASCADE,
    position         SMALLINT       NOT NULL,
    description      VARCHAR(500)   NOT NULL,
    unit             VARCHAR(30)    NOT NULL,
    quantity         NUMERIC(12, 2) NOT NULL,
    unit_price       NUMERIC(14, 2),
    PRIMARY KEY (service_order_id, position)
);

-- Termo de Recebimento e Avaliação (Anexo IX). Com ressalvas pode haver um novo termo depois.
CREATE TABLE service_order_receipt (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_order_id    BIGINT        NOT NULL REFERENCES service_order (id),
    kind                VARCHAR(30)   NOT NULL,   -- DEFINITIVE | WITH_RESERVATIONS | REJECTED
    execution_date      DATE          NOT NULL,
    reservations        VARCHAR(2000),
    correction_days     INT,
    reject_reason       VARCHAR(2000),
    score_deadline      INT           NOT NULL CHECK (score_deadline BETWEEN 1 AND 5),
    score_quality       INT           NOT NULL CHECK (score_quality BETWEEN 1 AND 5),
    score_documentation INT           NOT NULL CHECK (score_documentation BETWEEN 1 AND 5),
    score_service       INT           NOT NULL CHECK (score_service BETWEEN 1 AND 5),
    score_safety        INT           NOT NULL CHECK (score_safety BETWEEN 1 AND 5),
    average             NUMERIC(3, 2) NOT NULL,
    notes               VARCHAR(2000),
    evidence_kinds      VARCHAR(200), -- fotos, lista de presença, relatório...
    received_by         BIGINT        NOT NULL REFERENCES app_user (id),
    received_at         TIMESTAMPTZ   NOT NULL,
    supplier_ack_at     TIMESTAMPTZ
);
CREATE INDEX ix_receipt_order ON service_order_receipt (service_order_id, received_at);

-- Evidências do recebimento, nota fiscal/RPA e comprovante de pagamento.
CREATE TABLE service_order_file (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_order_id BIGINT       NOT NULL REFERENCES service_order (id),
    kind             VARCHAR(20)  NOT NULL,   -- EVIDENCE | INVOICE | PAYMENT_PROOF
    file_name        VARCHAR(200) NOT NULL,
    file_type        VARCHAR(100) NOT NULL,
    file_size        BIGINT       NOT NULL,
    file_sha256      VARCHAR(64)  NOT NULL,
    file_key         VARCHAR(200) NOT NULL,
    uploaded_by      BIGINT       NOT NULL REFERENCES app_user (id),
    uploaded_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_order_file ON service_order_file (service_order_id, kind);

-- Banco de Preços (aba da Planilha): preços praticados ou cotados por item.
CREATE TABLE price_bank_entry (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supply_line_id   SMALLINT       NOT NULL REFERENCES supply_line (id),
    item_description VARCHAR(500)   NOT NULL,
    unit             VARCHAR(30)    NOT NULL,
    unit_price       NUMERIC(14, 2) NOT NULL CHECK (unit_price > 0),
    source           VARCHAR(30)    NOT NULL,  -- CONTRACT | QUOTATION | PRICE_PANEL | RESEARCH
    reference        VARCHAR(100),             -- nº da OF, da oportunidade...
    application_id   BIGINT REFERENCES supplier_application (id),
    service_order_id BIGINT REFERENCES service_order (id),
    observed_on      DATE           NOT NULL,
    created_by       BIGINT REFERENCES app_user (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_price_bank_item ON price_bank_entry (supply_line_id, lower(item_description));
