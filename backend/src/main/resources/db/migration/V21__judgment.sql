-- =====================================================================
-- Fase 2 — parte B: julgamento das Faixas 2 e 3 (Edital 7.5, 7.6, 8.2 a 8.4; Manual 4.2):
-- desclassificação, mapa comparativo, sorteio, direito de cobrir, ata e homologação.
-- O mapa (judgment_entry) e o sorteio (draw_record) são imutáveis.
-- =====================================================================

-- tender.status ganha JUDGING (prazo encerrado, julgamento em curso).

ALTER TABLE proposal
    ADD COLUMN disqualified_reason VARCHAR(1000),
    ADD COLUMN disqualified_at     TIMESTAMPTZ,
    ADD COLUMN disqualified_by     BIGINT REFERENCES app_user (id);

CREATE TABLE judgment (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tender_id                   BIGINT         NOT NULL REFERENCES tender (id),
    -- AWAITING_COVER | DECIDED | HOMOLOGATED | ANNULLED
    status                      VARCHAR(20)    NOT NULL,
    lowest_value                NUMERIC(14, 2) NOT NULL,
    -- parâmetros vigentes no julgamento (a ata cita estes, não os atuais)
    tie_margin                  NUMERIC(8, 6)  NOT NULL,
    local_margin                NUMERIC(8, 6)  NOT NULL,
    local_preference_for_all    BOOLEAN        NOT NULL,
    cover_strictly_lower        BOOLEAN        NOT NULL,
    valid_count                 INT            NOT NULL,
    min_valid_required          INT            NOT NULL,
    below_minimum_justification VARCHAR(2000),
    winner_proposal_id          BIGINT REFERENCES proposal (id),
    awarded_value               NUMERIC(14, 2),
    winner_reason               VARCHAR(1000),
    started_by                  BIGINT         NOT NULL REFERENCES app_user (id),
    started_at                  TIMESTAMPTZ    NOT NULL,
    decided_at                  TIMESTAMPTZ,
    homologated_by              BIGINT REFERENCES app_user (id),
    homologated_at              TIMESTAMPTZ,
    homologation_note           VARCHAR(2000),
    annulled_by                 BIGINT REFERENCES app_user (id),
    annulled_at                 TIMESTAMPTZ,
    annul_reason                VARCHAR(2000),
    ata_key                     VARCHAR(200),
    ata_sha256                  VARCHAR(64),
    version                     BIGINT         NOT NULL DEFAULT 0
);
-- Um julgamento vivo por oportunidade; anulados ficam no histórico.
CREATE UNIQUE INDEX ux_judgment_active ON judgment (tender_id) WHERE status <> 'ANNULLED';
CREATE INDEX ix_judgment_status ON judgment (status);

-- Mapa comparativo congelado no momento do julgamento.
CREATE TABLE judgment_entry (
    judgment_id             BIGINT         NOT NULL REFERENCES judgment (id),
    proposal_id             BIGINT         NOT NULL REFERENCES proposal (id),
    application_id          BIGINT         NOT NULL REFERENCES supplier_application (id),
    position                INT,            -- nulo = desclassificada
    supplier_name           VARCHAR(300)   NOT NULL,
    supplier_type           VARCHAR(20),
    total_value             NUMERIC(14, 2) NOT NULL,
    pct_above               NUMERIC(12, 6),
    small_business          BOOLEAN        NOT NULL,
    territory               VARCHAR(20),
    woman_led               BOOLEAN        NOT NULL,
    black_woman_or_cad_unico BOOLEAN       NOT NULL,
    mei                     BOOLEAN        NOT NULL,
    rating                  NUMERIC(4, 2),
    fictitious_tie          BOOLEAN        NOT NULL,
    local_preference        BOOLEAN        NOT NULL,
    finalist                BOOLEAN        NOT NULL,
    drawn                   BOOLEAN        NOT NULL,
    advantage               VARCHAR(30),
    disqualified_reason     VARCHAR(1000),
    PRIMARY KEY (judgment_id, proposal_id)
);
CREATE TRIGGER trg_judgment_entry_immutable
    BEFORE UPDATE OR DELETE ON judgment_entry
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Sorteio (Edital 7.6): semente gravada; com ela o resultado se reproduz.
CREATE TABLE draw_record (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    judgment_id  BIGINT      NOT NULL REFERENCES judgment (id),
    seed         BIGINT      NOT NULL,
    algorithm    VARCHAR(100) NOT NULL,
    groups_json  JSONB       NOT NULL,   -- [[ids na ordem sorteada], ...]
    drawn_at     TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER trg_draw_record_immutable
    BEFORE UPDATE OR DELETE ON draw_record
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Direito de cobrir (Edital 8.2): uma convocação por vez, na ordem do mapa.
CREATE TABLE cover_offer (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    judgment_id    BIGINT         NOT NULL REFERENCES judgment (id),
    proposal_id    BIGINT         NOT NULL REFERENCES proposal (id),
    application_id BIGINT         NOT NULL REFERENCES supplier_application (id),
    position       INT            NOT NULL,
    -- OFFERED | COVERED | DECLINED | EXPIRED | CANCELLED
    status         VARCHAR(20)    NOT NULL,
    ceiling        NUMERIC(14, 2) NOT NULL,  -- menor preço a cobrir
    offered_at     TIMESTAMPTZ    NOT NULL,
    due_date       DATE           NOT NULL,
    responded_at   TIMESTAMPTZ,
    offered_value  NUMERIC(14, 2),
    version        BIGINT         NOT NULL DEFAULT 0,
    UNIQUE (judgment_id, proposal_id)
);
CREATE INDEX ix_cover_offer_pending ON cover_offer (due_date) WHERE status = 'OFFERED';
