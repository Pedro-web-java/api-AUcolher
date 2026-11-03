-- ============================================================
-- Migração 017 — categorias novas de evento
--
-- Entram Arrecadação (COLLECTION), Passeio e encontro (MEETUP) e
-- Outro (OTHER). Com OTHER, a ONG escreve o nome da categoria em
-- custom_category (até 30 caracteres), que aparece no selo do
-- evento; nas outras categorias a coluna fica nula.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/017_event_categories.sql
--
-- Troca o CHECK da categoria e acrescenta uma coluna nula (nenhum
-- dado muda). Rodar de novo não estraga nada (IF EXISTS / IF NOT EXISTS).
-- ============================================================

BEGIN;

ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check;
ALTER TABLE events ADD CONSTRAINT events_category_check
    CHECK (category IN ('ADOPTION_FAIR', 'HEALTH', 'BAZAAR', 'WORKSHOP', 'COLLECTION', 'MEETUP', 'OTHER'));

ALTER TABLE events ADD COLUMN IF NOT EXISTS custom_category VARCHAR(30);

COMMIT;
