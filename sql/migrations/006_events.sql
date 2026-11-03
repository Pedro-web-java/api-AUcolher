-- ============================================================
-- Migração 006 — eventos das ONGs e confirmações de presença
--
-- Cria events (eventos criados por ONGs) e event_attendances (quem
-- confirmou presença). Até aqui os eventos existiam só no navegador
-- (mock no localStorage do frontend).
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/006_events.sql
--
-- Roda numa transação só: se algo falhar, nada muda. Rodar de novo
-- não estraga nada (IF NOT EXISTS).
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- events — eventos criados por ONGs. Excluir um evento com
-- confirmados só muda o status para CANCELLED.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS events (
    id           BIGSERIAL     PRIMARY KEY,
    ngo_id       BIGINT        NOT NULL REFERENCES users(id),
    title        VARCHAR(120)  NOT NULL,
    description  TEXT          NOT NULL,
    category     VARCHAR(20)   NOT NULL CHECK (category IN ('ADOPTION_FAIR', 'HEALTH', 'BAZAAR', 'WORKSHOP')),
    date         DATE          NOT NULL,
    start_time   TIME          NOT NULL,
    end_time     TIME,
    venue_name   VARCHAR(120)  NOT NULL,
    cep          VARCHAR(8),
    street       VARCHAR(150)  NOT NULL,
    number       VARCHAR(20),
    complement   VARCHAR(100),
    district     VARCHAR(100),
    city         VARCHAR(100)  NOT NULL,
    state        VARCHAR(2)    NOT NULL,
    capacity     INTEGER       CHECK (capacity IS NULL OR capacity > 0),
    cover_url    TEXT,
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (end_time IS NULL OR end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_events_ngo ON events (ngo_id);
CREATE INDEX IF NOT EXISTS idx_events_status_date ON events (status, date);

-- ------------------------------------------------------------
-- event_attendances — confirmações de presença (uma por pessoa e
-- evento)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event_attendances (
    event_id      BIGINT     NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id       BIGINT     NOT NULL REFERENCES users(id),
    confirmed_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_event_attendances_user ON event_attendances (user_id);

COMMIT;
