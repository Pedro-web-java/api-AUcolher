-- ============================================================
-- Migração 016 — edição pela moderação
--
-- O admin pode editar contas (perfil e e-mail de login), anúncios de
-- animais e eventos de outras pessoas. O conteúdo continua em nome do
-- dono, então cada um guarda quando e por quem foi editado pela última
-- vez: o dono vê "Editado pela moderação em {data}".
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/016_moderation_edits.sql
--
-- Só acrescenta colunas nulas (nenhum dado muda). Rodar de novo não
-- estraga nada (IF NOT EXISTS).
-- ============================================================

BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS moderated_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE animals
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS moderated_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS moderated_by BIGINT REFERENCES users (id) ON DELETE SET NULL;

COMMENT ON COLUMN users.moderated_at   IS 'Última edição do perfil feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN users.moderated_by   IS 'O admin que fez a última edição do perfil';
COMMENT ON COLUMN animals.moderated_at IS 'Última edição do anúncio feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN animals.moderated_by IS 'O admin que fez a última edição do anúncio';
COMMENT ON COLUMN events.moderated_at  IS 'Última edição do evento feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN events.moderated_by  IS 'O admin que fez a última edição do evento';

COMMIT;
