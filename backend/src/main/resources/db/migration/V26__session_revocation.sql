-- Encerramento imediato dos access tokens (JWT) de um usuário: tokens emitidos antes
-- deste instante são recusados (desativação, troca de perfis, redefinição de senha).
ALTER TABLE app_user ADD COLUMN sessions_revoked_at TIMESTAMPTZ;
