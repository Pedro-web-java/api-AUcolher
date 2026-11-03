-- ============================================================
-- Migração 010 — segurança da senha
--
-- password_changed_at: tokens JWT emitidos antes da última
-- redefinição de senha deixam de valer (derruba as sessões).
-- password_reset_tokens: links de "Esqueci minha senha" (só o hash
-- do token fica no banco; uso único; 30 minutos).
--
-- psql -U postgres -h localhost -d aucolher_db -f sql/migrations/010_password_security.sql
-- ============================================================
BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;

COMMENT ON COLUMN users.password_changed_at IS 'Última troca ou redefinição de senha; tokens JWT com iat anterior são recusados';

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL  PRIMARY KEY,
    user_id     BIGINT     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)   NOT NULL UNIQUE,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP  NOT NULL,
    used_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user ON password_reset_tokens (user_id, created_at);

COMMENT ON TABLE  password_reset_tokens            IS 'Links de redefinição de senha (Esqueci minha senha)';
COMMENT ON COLUMN password_reset_tokens.token_hash IS 'SHA-256 (hex) do token enviado por e-mail — o token puro nunca é gravado';
COMMENT ON COLUMN password_reset_tokens.used_at    IS 'Preenchido quando o link é usado ou substituído por um novo';

COMMIT;
