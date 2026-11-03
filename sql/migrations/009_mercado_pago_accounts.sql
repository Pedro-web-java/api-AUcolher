-- ============================================================
-- Migração 009 — conta Mercado Pago de cada ONG
--
-- Cada ONG vincula a própria conta (OAuth do Mercado Pago) e as
-- doações caem direto nela. Os tokens ficam criptografados pela
-- aplicação (AES-256-GCM): o banco só vê texto cifrado.
--
-- psql -U postgres -h localhost -d aucolher_db -f sql/migrations/009_mercado_pago_accounts.sql
--
-- Se a sua migração 007 rodou sem donations.ngo_id, descomente o bloco abaixo.
-- ============================================================
BEGIN;

-- ALTER TABLE donations ADD COLUMN IF NOT EXISTS ngo_id BIGINT REFERENCES users (id);
-- UPDATE donations d SET ngo_id = c.ngo_id FROM campaigns c WHERE c.id = d.campaign_id AND d.ngo_id IS NULL;
-- ALTER TABLE donations ALTER COLUMN ngo_id SET NOT NULL;

CREATE TABLE IF NOT EXISTS mercado_pago_accounts (
    ngo_id         BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    mp_user_id     BIGINT       NOT NULL UNIQUE,
    access_token   TEXT         NOT NULL,
    refresh_token  TEXT         NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    live_mode      BOOLEAN      NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'CONNECTED',
    connected_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_mp_accounts_status CHECK (status IN ('CONNECTED', 'NEEDS_RECONNECT'))
);

-- O job diário procura as contas que vencem logo
CREATE INDEX IF NOT EXISTS idx_mp_accounts_expires ON mercado_pago_accounts (expires_at);

COMMENT ON TABLE  mercado_pago_accounts               IS 'Conta Mercado Pago vinculada por cada ONG (OAuth) — as doações caem direto nela';
COMMENT ON COLUMN mercado_pago_accounts.mp_user_id    IS 'user_id da conta no Mercado Pago; uma conta não serve a duas ONGs';
COMMENT ON COLUMN mercado_pago_accounts.access_token  IS 'Token de acesso da ONG, criptografado pela aplicação (AES-256-GCM)';
COMMENT ON COLUMN mercado_pago_accounts.refresh_token IS 'Token de renovação, criptografado; muda a cada renovação';
COMMENT ON COLUMN mercado_pago_accounts.status        IS 'CONNECTED ou NEEDS_RECONNECT (renovação falhou ou acesso revogado)';

COMMIT;
