-- ============================================================
-- Migração 014 — denúncias e moderação
--
-- 1. reports: as denúncias de anúncios e perfis, com a cópia do
--    alvo e a decisão do admin.
-- 2. animals.status passa a aceitar REMOVED (removido pela moderação).
-- 3. adoption_requests.cancel_reason passa a aceitar ACCOUNT_BANNED e
--    LISTING_REMOVED (e já ACCOUNT_DELETED, da exclusão de conta).
-- 4. events ganha removed_by_moderation.
-- O banimento não precisa de coluna nova: usa users.active.
--
-- campaigns ainda não existe no backend: quem criar a tabela (migração
-- 007) deve incluir nela a coluna removed_by_moderation BOOLEAN NOT NULL
-- DEFAULT FALSE, com CHECK (NOT removed_by_moderation OR deleted).
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/014_reports_moderation.sql
--
-- Roda numa transação só: se algo falhar, nada muda. Rodar de novo
-- não estraga nada.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 1. reports — denúncias feitas no site. O alvo é polimórfico
-- (target_type + target_id, sem chave estrangeira): pode ser um
-- animal ou uma conta. As colunas target_* guardam a cópia do alvo
-- no momento da denúncia, para o admin ver o que foi denunciado
-- mesmo depois de o anúncio sair do ar.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    id                      BIGSERIAL     PRIMARY KEY,
    reporter_id             BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type             VARCHAR(10)   NOT NULL,
    target_id               BIGINT        NOT NULL,

    -- Cópia do alvo
    target_name             VARCHAR(150)  NOT NULL,
    target_photo_url        TEXT,
    target_user_type        VARCHAR(10),
    target_city             VARCHAR(100),
    target_state            VARCHAR(2),
    target_species          VARCHAR(20),
    target_owner_id         BIGINT,
    target_owner_name       VARCHAR(150),
    target_owner_user_type  VARCHAR(10),

    -- Denúncia
    reason                  VARCHAR(20)   NOT NULL,
    description             VARCHAR(500),

    -- Julgamento
    status                  VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    resolution              VARCHAR(20),
    resolved_by             BIGINT        REFERENCES users (id),
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at             TIMESTAMP,

    CONSTRAINT chk_reports_target_type      CHECK (target_type IN ('ANIMAL', 'USER')),
    CONSTRAINT chk_reports_user_types       CHECK (target_user_type IN ('PERSON', 'NGO')
                                                AND target_owner_user_type IN ('PERSON', 'NGO')),
    CONSTRAINT chk_reports_reason           CHECK (reason IN ('FAKE_PROFILE', 'SCAM', 'ANIMAL_ABUSE',
                                                              'IMPROPER_CONTENT', 'SPAM', 'OTHER')),
    CONSTRAINT chk_reports_fake_profile     CHECK (reason <> 'FAKE_PROFILE' OR target_type = 'USER'),
    CONSTRAINT chk_reports_other            CHECK (reason <> 'OTHER' OR description IS NOT NULL),
    CONSTRAINT chk_reports_status           CHECK (status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT chk_reports_resolution       CHECK (resolution IN ('LISTING_REMOVED', 'ACCOUNT_BANNED', 'DISMISSED')),
    CONSTRAINT chk_reports_resolved         CHECK ((status = 'RESOLVED') = (resolution IS NOT NULL)
                                               AND (status = 'RESOLVED') = (resolved_at IS NOT NULL)),
    CONSTRAINT chk_reports_listing_removed  CHECK (resolution <> 'LISTING_REMOVED' OR target_type = 'ANIMAL')
);

-- Uma denúncia em análise por pessoa e alvo (o 409); julgada, libera outra
CREATE UNIQUE INDEX IF NOT EXISTS uq_reports_pending  ON reports (reporter_id, target_type, target_id)
    WHERE status = 'PENDING';
-- Resolver o caso inteiro (todas as pendentes do mesmo alvo)
CREATE INDEX IF NOT EXISTS idx_reports_pending_target ON reports (target_type, target_id)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_reports_created        ON reports (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_reporter       ON reports (reporter_id, created_at DESC);

COMMENT ON TABLE  reports                  IS 'Denúncias de anúncios (ANIMAL) e perfis (USER), julgadas pelo admin';
COMMENT ON COLUMN reports.target_id        IS 'Id do animal (ANIMAL) ou da conta (USER) — sem FK, porque o alvo é polimórfico';
COMMENT ON COLUMN reports.target_name      IS 'Cópia do nome do alvo no momento da denúncia (as outras colunas target_* também são cópias)';
COMMENT ON COLUMN reports.target_owner_id  IS 'Só em ANIMAL: o dono do anúncio — usado para resolver as denúncias quando ele é banido';
COMMENT ON COLUMN reports.description      IS 'Até 500 caracteres; obrigatória quando reason = OTHER';
COMMENT ON COLUMN reports.status           IS 'PENDING (em análise) ou RESOLVED (julgada)';
COMMENT ON COLUMN reports.resolution       IS 'Só em RESOLVED: LISTING_REMOVED, ACCOUNT_BANNED ou DISMISSED';
COMMENT ON COLUMN reports.resolved_by      IS 'O admin que julgou o caso';

-- ------------------------------------------------------------
-- 2. animals — anúncio removido pela moderação (o dono não reativa)
-- ------------------------------------------------------------
ALTER TABLE animals DROP CONSTRAINT IF EXISTS chk_animals_status;
ALTER TABLE animals ADD CONSTRAINT chk_animals_status
    CHECK (status IN ('AVAILABLE', 'ADOPTED', 'INACTIVE', 'REMOVED'));

COMMENT ON COLUMN animals.status IS 'AVAILABLE, ADOPTED, INACTIVE (retirado do ar pelo dono — exclusão lógica) ou REMOVED (removido pela moderação)';

-- ------------------------------------------------------------
-- 3. adoption_requests — cancelamentos da moderação (a lista mantém
-- ACCOUNT_DELETED, da exclusão de conta, para a 013 não precisar
-- recriar a CHECK)
-- ------------------------------------------------------------
ALTER TABLE adoption_requests DROP CONSTRAINT IF EXISTS chk_adoption_requests_cancel_reason;
ALTER TABLE adoption_requests ADD CONSTRAINT chk_adoption_requests_cancel_reason
    CHECK (cancel_reason IN ('ADOPTER_WITHDREW', 'OWNER_WITHDREW', 'ANIMAL_ADOPTED',
                             'ANIMAL_UNAVAILABLE', 'ACCOUNT_DELETED', 'ACCOUNT_BANNED', 'LISTING_REMOVED'));

COMMENT ON COLUMN adoption_requests.cancel_reason IS 'Só em CANCELLED: ADOPTER_WITHDREW, OWNER_WITHDREW, ANIMAL_ADOPTED, ANIMAL_UNAVAILABLE, ACCOUNT_BANNED (uma das partes foi banida) ou LISTING_REMOVED (anúncio removido pela moderação)';

-- ------------------------------------------------------------
-- 4. events — o que foi removido pela moderação fica separado do
-- que a própria ONG cancelou
-- ------------------------------------------------------------
ALTER TABLE events ADD COLUMN IF NOT EXISTS removed_by_moderation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_removed_by_moderation;
ALTER TABLE events ADD CONSTRAINT chk_events_removed_by_moderation
    CHECK (NOT removed_by_moderation OR status = 'CANCELLED');

COMMENT ON COLUMN events.removed_by_moderation IS 'Cancelado pelo admin (sempre com status CANCELLED)';

-- ------------------------------------------------------------
-- users.active já existe: false = conta banida pela moderação
-- ------------------------------------------------------------
COMMENT ON COLUMN users.active IS 'false = conta suspensa pela moderação: não entra e o conteúdo some das rotas públicas';

COMMIT;
