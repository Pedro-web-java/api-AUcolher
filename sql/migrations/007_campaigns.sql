-- ============================================================
-- Migração 007 — campanhas e doações (PIX)
--
-- campaigns: campanhas de arrecadação das ONGs, já com as colunas
-- de moderação (removed_by_moderation, moderated_at, moderated_by).
-- donations: doações por PIX do Mercado Pago. campaign_title e
-- ngo_id ficam guardados na doação para o histórico continuar
-- legível (e a order consultável) se a campanha for apagada de vez.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/007_campaigns.sql
--
-- Só cria o que falta e reescreve os comentários: rodar de novo
-- não estraga nada (IF NOT EXISTS).
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS campaigns (
    id                     BIGSERIAL      PRIMARY KEY,
    ngo_id                 BIGINT         NOT NULL REFERENCES users (id),
    title                  VARCHAR(120)   NOT NULL,
    description            TEXT           NOT NULL,
    category               VARCHAR(20)    NOT NULL,
    custom_category        VARCHAR(30),
    is_urgent              BOOLEAN        NOT NULL DEFAULT FALSE,
    goal_amount            NUMERIC(12,2)  NOT NULL,
    deadline               DATE,
    cover_url              TEXT,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    deleted                BOOLEAN        NOT NULL DEFAULT FALSE,
    removed_by_moderation  BOOLEAN        NOT NULL DEFAULT FALSE,
    moderated_at           TIMESTAMP,
    moderated_by           BIGINT         REFERENCES users (id) ON DELETE SET NULL,
    created_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at              TIMESTAMP,

    CONSTRAINT chk_campaigns_category CHECK (category IN ('HEALTH', 'FOOD', 'INFRASTRUCTURE', 'NEUTERING', 'RESCUE', 'OTHER')),
    CONSTRAINT chk_campaigns_status   CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_campaigns_goal     CHECK (goal_amount >= 50),
    CONSTRAINT chk_campaigns_removed_by_moderation CHECK (NOT removed_by_moderation OR deleted)
);

CREATE INDEX IF NOT EXISTS idx_campaigns_ngo ON campaigns (ngo_id);

CREATE TABLE IF NOT EXISTS donations (
    id              BIGSERIAL      PRIMARY KEY,
    campaign_id     BIGINT         REFERENCES campaigns (id) ON DELETE SET NULL,
    campaign_title  VARCHAR(120)   NOT NULL,
    ngo_id          BIGINT         NOT NULL REFERENCES users (id),
    donor_id        BIGINT         NOT NULL REFERENCES users (id),
    amount          NUMERIC(12,2)  NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    mp_order_id     VARCHAR(64),
    pix_qr_code     TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP      NOT NULL,
    approved_at     TIMESTAMP,

    CONSTRAINT uq_donations_mp_order_id UNIQUE (mp_order_id),
    CONSTRAINT chk_donations_amount     CHECK (amount >= 5),
    CONSTRAINT chk_donations_status     CHECK (status IN ('PENDING', 'APPROVED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_donations_campaign_status ON donations (campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_donations_donor           ON donations (donor_id);
CREATE INDEX IF NOT EXISTS idx_donations_ngo_status      ON donations (ngo_id, status);

COMMENT ON TABLE  campaigns                       IS 'Campanhas de arrecadação criadas por ONGs; doações via PIX do Mercado Pago';
COMMENT ON COLUMN campaigns.custom_category       IS 'Nome livre da categoria quando category = OTHER; nulo nas demais';
COMMENT ON COLUMN campaigns.status                IS 'ACTIVE ou CLOSED (encerrada pela ONG). Prazo vencido também conta como encerrada, mas é calculado na leitura';
COMMENT ON COLUMN campaigns.deleted               IS 'Exclusão lógica: some das rotas de campanha, mas as doações aprovadas continuam no histórico';
COMMENT ON COLUMN campaigns.removed_by_moderation IS 'Removida pelo admin (sempre com deleted = true)';
COMMENT ON COLUMN campaigns.moderated_at          IS 'Última edição da campanha feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN campaigns.moderated_by          IS 'O admin que fez a última edição da campanha';

COMMENT ON TABLE  donations                IS 'Doações PIX às campanhas. PENDING vale 30 min; depois vira EXPIRED (calculado na leitura)';
COMMENT ON COLUMN donations.campaign_title IS 'Título no momento da doação: o histórico continua legível se a campanha for apagada de vez';
COMMENT ON COLUMN donations.ngo_id         IS 'ONG dona no momento da doação: diz qual token do Mercado Pago consulta a order';

COMMIT;
