-- Verificação em duas etapas por código de 6 dígitos enviado por e-mail. Obrigatória para a
-- equipe interna; o fornecedor liga em "Minha conta" (two_factor_enabled).
ALTER TABLE app_user ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Login que passou pela senha e aguarda o código. O navegador guarda só o identificador
-- opaco (challenge); o banco guarda o hash dele e o hash do código.
CREATE TABLE login_challenge (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    challenge_hash  VARCHAR(64) NOT NULL UNIQUE,
    code_hash       VARCHAR(64),
    attempts        INT         NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    last_sent_at    TIMESTAMPTZ,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_challenge_user ON login_challenge (user_id);
