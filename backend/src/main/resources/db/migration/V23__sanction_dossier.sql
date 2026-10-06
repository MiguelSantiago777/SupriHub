-- =====================================================================
-- Fase 3 — parte B: processo sancionador (Edital 11; Anexo XII; Política art. 24 a 26),
-- dossiê da contratação (Anexo XIII; Política art. 20) e revisão por amostragem.
-- =====================================================================

INSERT INTO parameter (key, value, value_type, category, description, source) VALUES
('sanction_defense_business_days',        '5',    'INTEGER', 'SANCOES', 'Prazo de defesa no processo sancionador (dias úteis)', 'Política art. 26; Anexo XII'),
('sanction_decision_business_days',       '10',   'INTEGER', 'SANCOES', 'Prazo da decisão motivada da Comissão (dias úteis)', 'Política art. 26; Anexo XII'),
('sanction_appeal_business_days',         '5',    'INTEGER', 'SANCOES', 'Prazo do recurso à Diretoria (dias úteis)', 'Política art. 26; Anexo XII'),
('sanction_board_decision_business_days', '10',   'INTEGER', 'SANCOES', 'Prazo da decisão final da Diretoria (dias úteis)', 'Política art. 26; Anexo XII'),
('sanction_recidivism_years',             '5',    'INTEGER', 'SANCOES', 'Reincidência: nova infração dentro deste prazo (anos)', 'Anexo XII'),
('dossier_review_sample_share',           '0.10', 'DECIMAL', 'SANCOES', 'Amostra mínima de dossiês revisados pelo Controle Interno no semestre', 'Manual item 1 (Controle Interno)');

-- Bloqueio do credenciado: suspensão, descredenciamento (impedimento) ou inativação cautelar.
ALTER TABLE supplier_application
    ADD COLUMN blocked_until DATE,
    ADD COLUMN block_reason  VARCHAR(300);

CREATE SEQUENCE sanction_number_seq;

CREATE TABLE sanction_process (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number                VARCHAR(20)   NOT NULL UNIQUE,   -- PS-AAAA-NNNN
    application_id        BIGINT        NOT NULL REFERENCES supplier_application (id),
    service_order_id      BIGINT        REFERENCES service_order (id),
    infraction            VARCHAR(40)   NOT NULL,
    facts                 VARCHAR(5000) NOT NULL,
    -- DEFENSE | AWAITING_DECISION | APPEAL_WINDOW | BOARD_REVIEW | FINAL
    status                VARCHAR(30)   NOT NULL,
    interim_block         BOOLEAN       NOT NULL DEFAULT FALSE,
    opened_by             BIGINT        NOT NULL REFERENCES app_user (id),
    opened_at             TIMESTAMPTZ   NOT NULL,
    defense_due           DATE          NOT NULL,
    defense_text          VARCHAR(5000),
    defense_at            TIMESTAMPTZ,
    decision_due          DATE,
    -- Decisão da Comissão: NONE | WARNING | FINE | SUSPENSION | DEREGISTRATION
    sanction              VARCHAR(20),
    fine_pct              NUMERIC(5, 2),
    suspension_months     INT,
    impediment_years      INT,
    decision_reason       VARCHAR(5000),
    decided_by            BIGINT REFERENCES app_user (id),
    decided_at            TIMESTAMPTZ,
    appeal_due            DATE,
    appeal_text           VARCHAR(5000),
    appealed_at           TIMESTAMPTZ,
    board_due             DATE,
    -- Diretoria: UPHELD | REFORMED | ANNULLED
    board_outcome         VARCHAR(20),
    board_reason          VARCHAR(5000),
    board_by              BIGINT REFERENCES app_user (id),
    board_at              TIMESTAMPTZ,
    -- Sanção que vale ao final (pode ter sido reformada pela Diretoria)
    final_sanction        VARCHAR(20),
    final_fine_pct        NUMERIC(5, 2),
    final_months          INT,
    final_years           INT,
    effective_until       DATE,
    final_at              TIMESTAMPTZ,
    version               BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_sanction_status ON sanction_process (status);
CREATE INDEX ix_sanction_application ON sanction_process (application_id, final_at);

CREATE TABLE sanction_file (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_id   BIGINT       NOT NULL REFERENCES sanction_process (id),
    kind         VARCHAR(20)  NOT NULL,   -- EVIDENCE | DEFENSE | APPEAL
    file_name    VARCHAR(200) NOT NULL,
    file_type    VARCHAR(100) NOT NULL,
    file_size    BIGINT       NOT NULL,
    file_sha256  VARCHAR(64)  NOT NULL,
    file_key     VARCHAR(200) NOT NULL,
    uploaded_by  BIGINT       NOT NULL REFERENCES app_user (id),
    uploaded_at  TIMESTAMPTZ  NOT NULL
);

-- Itens do Anexo XIII que o sistema não tem como saber sozinho (atesto, publicação no sítio...).
CREATE TABLE dossier_check (
    service_order_id BIGINT       NOT NULL REFERENCES service_order (id),
    item_no          SMALLINT     NOT NULL,
    checked          BOOLEAN      NOT NULL,
    note             VARCHAR(500),
    checked_by       BIGINT       NOT NULL REFERENCES app_user (id),
    checked_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (service_order_id, item_no)
);

-- Revisão por amostragem do Controle Interno (mínimo 10% ao semestre).
CREATE TABLE dossier_review (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_order_id BIGINT        NOT NULL REFERENCES service_order (id),
    complete         BOOLEAN       NOT NULL,
    note             VARCHAR(2000),
    reviewed_by      BIGINT        NOT NULL REFERENCES app_user (id),
    reviewed_at      TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_dossier_review_order ON dossier_review (service_order_id, reviewed_at);
