-- =====================================================================
-- Fase 2 — parte A: oportunidades/editais (DFD-NATA, parte 5 — Anexo VI), rodízio da
-- Faixa 1 e propostas (Anexo VII). O julgamento (empate ficto, preferência local,
-- desempate, cobertura e ata) entra na parte B.
-- =====================================================================

CREATE SEQUENCE tender_number_seq;

CREATE TABLE tender (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number                  VARCHAR(20)    UNIQUE,         -- OP-AAAA-NNNN, atribuído na publicação
    -- DRAFT | OPEN | AWARDED (Faixa 1 aceita) | CANCELLED
    status                  VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    title                   VARCHAR(200)   NOT NULL,
    description             VARCHAR(5000)  NOT NULL,
    supply_line_id          SMALLINT       NOT NULL REFERENCES supply_line (id),
    execution_municipality_id INT REFERENCES municipality (id),
    location                VARCHAR(300),
    schedule                VARCHAR(300),   -- data(s) e horário(s)
    execution_deadline      VARCHAR(300),   -- prazo de execução/entrega
    requirements            VARCHAR(3000),
    payment_terms           VARCHAR(500),
    -- ALL | WOMEN (exclusiva a negócios liderados por mulheres, Edital 8.5)
    audience                VARCHAR(20)    NOT NULL DEFAULT 'ALL',
    estimated_value         NUMERIC(14, 2) NOT NULL,       -- nunca exibido ao fornecedor
    band                    VARCHAR(10)    NOT NULL,       -- FAIXA_1 | FAIXA_2 | FAIXA_3
    proposal_deadline       TIMESTAMPTZ,                   -- Faixas 2 e 3
    published_at            TIMESTAMPTZ,
    cancel_reason           VARCHAR(1000),
    created_by              BIGINT         NOT NULL REFERENCES app_user (id),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version                 BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_tender_status ON tender (status, proposal_deadline);

CREATE TABLE tender_territory (
    tender_id BIGINT     NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    territory VARCHAR(4) NOT NULL,
    PRIMARY KEY (tender_id, territory)
);

CREATE TABLE tender_item (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tender_id   BIGINT         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    position    SMALLINT       NOT NULL,
    description VARCHAR(500)   NOT NULL,
    unit        VARCHAR(30)    NOT NULL,
    quantity    NUMERIC(12, 2) NOT NULL CHECK (quantity > 0),
    UNIQUE (tender_id, position)
);

-- Rodízio da Faixa 1 (Política art. 13; Manual 4.1): convite ao próximo da fila.
CREATE TABLE tender_invitation (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tender_id       BIGINT        NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    application_id  BIGINT        NOT NULL REFERENCES supplier_application (id),
    -- INVITED | ACCEPTED | CONFIRMED | DECLINED | EXPIRED | PRICE_REJECTED
    status          VARCHAR(20)   NOT NULL DEFAULT 'INVITED',
    invited_at      TIMESTAMPTZ   NOT NULL,
    due_date        DATE          NOT NULL,
    responded_at    TIMESTAMPTZ,
    decline_reason  VARCHAR(500),
    UNIQUE (tender_id, application_id)
);
CREATE INDEX ix_tender_invitation_pending ON tender_invitation (due_date) WHERE status = 'INVITED';
CREATE INDEX ix_tender_invitation_application ON tender_invitation (application_id, status);

CREATE TABLE proposal (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tender_id               BIGINT         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    application_id          BIGINT         NOT NULL REFERENCES supplier_application (id),
    -- ONLINE (preenchida no sistema) | MODEL (modelo baixado e assinado) | OWN_FILE (proposta própria)
    mode                    VARCHAR(20)    NOT NULL,
    total_value             NUMERIC(14, 2) NOT NULL CHECK (total_value > 0),
    execution_deadline      VARCHAR(300),
    validity_days           INT            NOT NULL,
    special_conditions      VARCHAR(2000),
    notes                   VARCHAR(2000),
    declaration_accepted_at TIMESTAMPTZ    NOT NULL,
    file_name               VARCHAR(200),
    file_type               VARCHAR(100),
    file_size               BIGINT,
    file_sha256             VARCHAR(64),
    file_key                VARCHAR(200),
    submitted_at            TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    UNIQUE (tender_id, application_id)
);

CREATE TABLE proposal_item (
    proposal_id    BIGINT         NOT NULL REFERENCES proposal (id) ON DELETE CASCADE,
    tender_item_id BIGINT         NOT NULL REFERENCES tender_item (id) ON DELETE CASCADE,
    unit_price     NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (proposal_id, tender_item_id)
);
