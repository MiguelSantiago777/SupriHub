-- =====================================================================
-- SupriHub — esquema núcleo (Fase 0)
-- Autenticação, catálogo, parâmetros, calendário, auditoria, notificações
-- e mensageria (transactional outbox).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Autenticação e perfis
-- ---------------------------------------------------------------------
CREATE TABLE role (
    id          SMALLINT PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL
);

CREATE TABLE app_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_logins   INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

CREATE TABLE user_role (
    user_id BIGINT   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_token (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by BIGINT REFERENCES refresh_token (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

CREATE TABLE email_verification_token (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    purpose     VARCHAR(30) NOT NULL, -- EMAIL_CONFIRMATION | PASSWORD_RESET
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Catálogo de linhas (Anexo I) e municípios
-- ---------------------------------------------------------------------
CREATE TABLE supply_line (
    id                  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(10)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    short_name          VARCHAR(100) NOT NULL,
    scope               TEXT         NOT NULL,
    cnae_reference      VARCHAR(300) NOT NULL,
    allows_individual   BOOLEAN      NOT NULL,
    allows_medium_large BOOLEAN      NOT NULL DEFAULT FALSE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order       SMALLINT     NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE municipality (
    id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ibge_code           VARCHAR(7)   NOT NULL UNIQUE,
    name                VARCHAR(120) NOT NULL,
    uf                  VARCHAR(2)   NOT NULL,
    metropolitan_region BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_municipality_name ON municipality (uf, lower(name));

-- ---------------------------------------------------------------------
-- Parâmetros (nunca em código) e histórico
-- ---------------------------------------------------------------------
CREATE TABLE parameter (
    key          VARCHAR(80)  PRIMARY KEY,
    value        VARCHAR(500) NOT NULL,
    value_type   VARCHAR(20)  NOT NULL, -- DECIMAL | INTEGER | BOOLEAN | TEXT
    category     VARCHAR(40)  NOT NULL,
    description  VARCHAR(300) NOT NULL,
    source       VARCHAR(300),
    updated_by   BIGINT REFERENCES app_user (id),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE parameter_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key         VARCHAR(80)  NOT NULL REFERENCES parameter (key),
    old_value   VARCHAR(500),
    new_value   VARCHAR(500) NOT NULL,
    reason      VARCHAR(500),
    changed_by  BIGINT REFERENCES app_user (id),
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_parameter_history_key ON parameter_history (key, changed_at DESC);

-- ---------------------------------------------------------------------
-- Calendário de dias não úteis (nacionais, RJ, Mesquita)
-- ---------------------------------------------------------------------
CREATE TABLE business_holiday (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    holiday_date    DATE         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    scope           VARCHAR(20)  NOT NULL, -- NACIONAL | ESTADUAL | MUNICIPAL
    optional_point  BOOLEAN      NOT NULL DEFAULT FALSE, -- ponto facultativo
    municipality_id INT REFERENCES municipality (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (holiday_date, scope, municipality_id)
);
CREATE INDEX ix_business_holiday_date ON business_holiday (holiday_date);

-- ---------------------------------------------------------------------
-- Auditoria (imutável)
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type  VARCHAR(80)  NOT NULL,
    entity_id    VARCHAR(80)  NOT NULL,
    action       VARCHAR(60)  NOT NULL,
    actor_id     BIGINT REFERENCES app_user (id),
    actor_label  VARCHAR(120),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    before_json  JSONB,
    after_json   JSONB,
    details      VARCHAR(1000)
);
CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Registro imutável: % não permite % ', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ---------------------------------------------------------------------
-- Notificações enviadas (registro de entrega)
-- ---------------------------------------------------------------------
CREATE TABLE notification (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id  BIGINT REFERENCES app_user (id),
    channel       VARCHAR(20)  NOT NULL, -- EMAIL | WHATSAPP
    template      VARCHAR(80)  NOT NULL,
    subject       VARCHAR(250),
    status        VARCHAR(20)  NOT NULL, -- SENT | FAILED | SKIPPED
    error         VARCHAR(1000),
    source_event  UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_recipient ON notification (recipient_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Mensageria: transactional outbox + idempotência de consumidores
-- ---------------------------------------------------------------------
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(80)  NOT NULL,
    aggregate_id    VARCHAR(80)  NOT NULL,
    event_type      VARCHAR(120) NOT NULL,
    payload         JSONB        NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | PUBLISHED | FAILED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      VARCHAR(2000),
    published_at    TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_event (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_aggregate ON outbox_event (aggregate_type, aggregate_id);

CREATE TABLE processed_message (
    consumer     VARCHAR(80) NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);

-- ---------------------------------------------------------------------
-- ShedLock (jobs agendados em cluster)
-- ---------------------------------------------------------------------
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- ---------------------------------------------------------------------
-- Perfis de acesso
-- ---------------------------------------------------------------------
INSERT INTO role (id, code, description) VALUES
    (1, 'SUPPLIER',         'Fornecedor com conta'),
    (2, 'COMMISSION',       'Comissão de Credenciamento'),
    (3, 'DEMAND_MANAGER',   'Gestor da demanda (coordenação de projeto)'),
    (4, 'FINANCE',          'Setor Financeiro'),
    (5, 'INTERNAL_CONTROL', 'Controle Interno / Compliance'),
    (6, 'BOARD',            'Diretoria'),
    (7, 'ADMIN',            'Administração do sistema (TI)');
