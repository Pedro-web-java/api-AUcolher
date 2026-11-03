-- ============================================================
-- Migração 004 — Perfil AUmatch (respostas do quiz)
--
-- Cria a tabela match_profiles: uma linha por pessoa com as 7
-- respostas do quiz do AUmatch, que até aqui ficavam só no
-- navegador (localStorage). A API usa essas respostas para
-- calcular a compatibilidade do deck (GET /api/match/deck).
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/004_match_profiles.sql
--
-- Roda numa transação só: se algo falhar, nada muda. Rodar de novo
-- não estraga nada (CREATE TABLE IF NOT EXISTS).
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS match_profiles (
    user_id             BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,

    -- Estilo de vida
    housing             VARCHAR(20),
    exercise_routine    VARCHAR(20),
    time_away           VARCHAR(20),
    has_children        BOOLEAN,
    has_dogs            BOOLEAN,
    has_cats            BOOLEAN,

    -- Preferências para o pet
    species_preference  VARCHAR(10),
    ideal_pet_profile   VARCHAR(30),
    preferred_size      VARCHAR(10),

    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_match_profiles_housing            CHECK (housing IN ('APARTMENT', 'HOUSE_WITH_YARD', 'FARM')),
    CONSTRAINT chk_match_profiles_exercise_routine   CHECK (exercise_routine IN ('SEDENTARY', 'ACTIVE', 'VERY_ACTIVE')),
    CONSTRAINT chk_match_profiles_time_away          CHECK (time_away IN ('SHORT', 'REGULAR', 'LONG')),
    CONSTRAINT chk_match_profiles_household          CHECK ((has_children IS NULL) = (has_dogs IS NULL)
                                                        AND (has_dogs IS NULL) = (has_cats IS NULL)),
    CONSTRAINT chk_match_profiles_species_preference CHECK (species_preference IN ('DOG', 'CAT', 'BOTH')),
    CONSTRAINT chk_match_profiles_ideal_pet_profile  CHECK (ideal_pet_profile IN ('CALM_COMPANION', 'PLAYFUL_ACTIVE', 'PROTECTIVE_INDEPENDENT')),
    CONSTRAINT chk_match_profiles_preferred_size     CHECK (preferred_size IN ('SMALL', 'MEDIUM', 'LARGE'))
);

COMMENT ON TABLE  match_profiles                  IS 'Perfil AUmatch: respostas do quiz de cada pessoa, usadas no cálculo de compatibilidade';
COMMENT ON COLUMN match_profiles.user_id          IS 'A pessoa (PERSON) dona das respostas — uma linha por conta';
COMMENT ON COLUMN match_profiles.time_away        IS 'Tempo fora de casa: SHORT (até 4h), REGULAR (4h a 8h) ou LONG (mais de 8h)';
COMMENT ON COLUMN match_profiles.has_children     IS 'Mora com crianças — as três colunas has_* nulas juntas = pergunta sem resposta';
COMMENT ON COLUMN match_profiles.preferred_size   IS 'Porte preferido; se estiver vazio, quem mora em APARTMENT conta como SMALL';

COMMIT;
