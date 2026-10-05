-- =====================================================================
-- Fase 1 — Requerimento de credenciamento (Edital item 3; Anexos II, III, IV e V)
-- e análise de habilitação pela Comissão (Edital item 4; Manual, item 3).
-- Um requerimento por conta de fornecedor.
-- =====================================================================

CREATE SEQUENCE supplier_application_protocol_seq;

CREATE TABLE supplier_application (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT       NOT NULL UNIQUE REFERENCES app_user (id),
    -- DRAFT | SUBMITTED | ADJUSTMENT_REQUESTED | APPROVED | REJECTED
    status                  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    protocol                VARCHAR(20)  UNIQUE, -- atribuído no primeiro envio (Manual: "nº automático")

    -- 1. Identificação (Anexo II)
    supplier_type           VARCHAR(20),  -- MEI | ME | EPP | COOPERATIVE | INDIVIDUAL
    legal_name              VARCHAR(200), -- razão social ou nome completo (PF)
    trade_name              VARCHAR(200),
    document_number         VARCHAR(14),  -- CNPJ ou CPF, só dígitos
    birth_date              DATE,         -- só PF (Edital 2.1.3, maior de 18 anos)
    street                  VARCHAR(200),
    address_number          VARCHAR(20),
    address_complement      VARCHAR(100),
    district                VARCHAR(100),
    municipality_id         INT REFERENCES municipality (id),
    cep                     VARCHAR(8),
    phone                   VARCHAR(11),  -- WhatsApp, só dígitos com DDD
    contact_email           VARCHAR(254),
    website                 VARCHAR(300),
    representative_name     VARCHAR(200),
    representative_cpf      VARCHAR(11),
    representative_role     VARCHAR(100),

    -- 2 e 3. Linhas e territórios
    lines_description       VARCHAR(3000),
    territories_note        VARCHAR(500),

    -- 4. Dados para pagamento (conta de titularidade do interessado)
    bank_name               VARCHAR(100),
    bank_branch             VARCHAR(10),
    bank_account            VARCHAR(20),
    bank_account_type       VARCHAR(20),  -- CHECKING | SAVINGS | PAYMENT
    pix_key                 VARCHAR(140),
    account_holder          VARCHAR(200),

    -- 5. Equidade (facultativo; nunca publicado individualmente — D-016)
    woman_led               BOOLEAN      NOT NULL DEFAULT FALSE,
    race                    VARCHAR(20),  -- PRETA | PARDA | INDIGENA | BRANCA | AMARELA | NAO_INFORMADA
    cad_unico               BOOLEAN      NOT NULL DEFAULT FALSE,
    nis                     VARCHAR(11),

    -- Declarações eletrônicas (Anexos III e V; Anexo IV quando woman_led)
    declarations_accepted_at TIMESTAMPTZ,

    -- Análise
    submitted_at            TIMESTAMPTZ,
    analysis_due_date       DATE,         -- 10 d.u. do requerimento completo (Edital 4.3)
    adjustment_message      VARCHAR(3000),
    adjustment_due_date     DATE,         -- 5 d.u. para atender (Edital 4.3)
    decided_at              TIMESTAMPTZ,
    decided_by              BIGINT REFERENCES app_user (id),
    decision_reason         VARCHAR(3000),
    accreditation_valid_until DATE,       -- 24 meses (Política art. 22)

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_supplier_application_status ON supplier_application (status, submitted_at);

CREATE TABLE supplier_application_line (
    application_id BIGINT   NOT NULL REFERENCES supplier_application (id) ON DELETE CASCADE,
    supply_line_id SMALLINT NOT NULL REFERENCES supply_line (id),
    PRIMARY KEY (application_id, supply_line_id)
);

CREATE TABLE supplier_application_territory (
    application_id BIGINT     NOT NULL REFERENCES supplier_application (id) ON DELETE CASCADE,
    territory      VARCHAR(4) NOT NULL, -- T1 | T2 | T3
    PRIMARY KEY (application_id, territory)
);

-- Documentos de habilitação (Edital 4.1): um arquivo por tipo.
CREATE TABLE supplier_document (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id    BIGINT       NOT NULL REFERENCES supplier_application (id) ON DELETE CASCADE,
    doc_type          VARCHAR(40)  NOT NULL,
    original_filename VARCHAR(200) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    sha256            VARCHAR(64)  NOT NULL,
    storage_key       VARCHAR(200) NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (application_id, doc_type)
);

-- Andamento visível ao fornecedor e à Comissão (a trilha completa fica em audit_log).
CREATE TABLE supplier_application_event (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT       NOT NULL REFERENCES supplier_application (id) ON DELETE CASCADE,
    status         VARCHAR(30)  NOT NULL,
    message        VARCHAR(3000),
    actor_id       BIGINT REFERENCES app_user (id),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_supplier_application_event ON supplier_application_event (application_id, occurred_at);
