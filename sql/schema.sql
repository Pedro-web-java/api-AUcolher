-- ============================================================
-- AUcolher — Script de criação do banco de dados (PostgreSQL)
-- Banco: aucolher_db
--
-- Execute com o psql, conectado ao banco padrão "postgres":
--   psql -U postgres -h localhost -f script_banco_aucolher.sql
--
-- \gexec e \c são comandos do psql. No pgAdmin: crie o banco
-- aucolher_db pela interface, abra o Query Tool nele e rode a
-- partir da seção "Tabelas".
--
-- Banco criado pela versão anterior deste script (tabelas em
-- português)? Não rode este: aplique, em ordem, os scripts de
-- sql/migrations/ — eles renomeiam tudo sem perder os dados.
--
-- Espelha as entidades do Spring Boot (User, TeamMember,
-- VisitingHour, Animal, Favorite, Event, MatchProfile) e a seção 6 de
-- docs/regras-de-negocio.md.
-- Cópia mantida no backend em sql/schema.sql.
-- ============================================================

-- PostgreSQL não tem CREATE DATABASE IF NOT EXISTS: o SELECT só gera
-- o comando quando o banco ainda não existe, e o \gexec o executa.
-- template0 evita conflito de encoding com o template1 no Windows.
SELECT 'CREATE DATABASE aucolher_db WITH TEMPLATE = template0 ENCODING = ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'aucolher_db')\gexec

\c aucolher_db

-- ============================================================
-- Tabelas
-- ============================================================

-- ------------------------------------------------------------
-- users
-- Os dois perfis (NGO e PERSON) e as duas formas de
-- autenticação (LOCAL e GOOGLE/OAuth2) na mesma tabela.
-- "Número de adoções realizadas" (seção 6.2) não é coluna: é
-- derivado dos registros de adoção.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                   BIGSERIAL     PRIMARY KEY,
    name                 VARCHAR(150)  NOT NULL,
    email                VARCHAR(150)  NOT NULL,
    password             VARCHAR(255),
    user_type            VARCHAR(20)   NOT NULL,
    provider             VARCHAR(20)   NOT NULL DEFAULT 'LOCAL',
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Perfil (comum e ONG)
    photo_url            TEXT,
    bio                  TEXT,

    -- Perfil de ONG
    cnpj                 VARCHAR(14),
    institutional_email  VARCHAR(150),
    is_verified          BOOLEAN       NOT NULL DEFAULT FALSE,
    instagram            VARCHAR(30),
    twitter              VARCHAR(15),
    facebook             VARCHAR(255),
    founded_year         INTEGER,

    -- Endereço (obrigatório para ONG, validado na API)
    cep                  VARCHAR(8),
    street               VARCHAR(150),
    number               VARCHAR(20),
    complement           VARCHAR(100),
    district             VARCHAR(100),
    city                 VARCHAR(100),
    state                VARCHAR(2),

    CONSTRAINT uq_users_email                UNIQUE (email),
    CONSTRAINT uq_users_cnpj                 UNIQUE (cnpj),
    CONSTRAINT chk_users_user_type           CHECK (user_type IN ('NGO', 'PERSON', 'ADMIN')),
    CONSTRAINT chk_users_provider            CHECK (provider IN ('LOCAL', 'GOOGLE')),
    CONSTRAINT chk_users_ngo_has_cnpj        CHECK (user_type <> 'NGO' OR cnpj IS NOT NULL),
    CONSTRAINT chk_users_local_has_password  CHECK (provider <> 'LOCAL' OR password IS NOT NULL),
    CONSTRAINT chk_users_founded_year        CHECK (founded_year IS NULL OR (user_type = 'NGO' AND founded_year >= 1800))
);

CREATE INDEX IF NOT EXISTS idx_users_user_type ON users (user_type);

COMMENT ON TABLE  users                     IS 'Contas da plataforma: ONGs/abrigos, usuários comuns e administradores';
COMMENT ON COLUMN users.password            IS 'Hash BCrypt — nulo para contas criadas via OAuth2 (Google)';
COMMENT ON COLUMN users.photo_url           IS 'URL do avatar ou data URL da imagem comprimida no frontend (enquanto não há upload próprio)';
COMMENT ON COLUMN users.bio                 IS 'Bio do usuário comum (cuidadores autônomos) ou descrição da ONG';
COMMENT ON COLUMN users.cnpj                IS 'Apenas os 14 dígitos — obrigatório quando user_type = NGO';
COMMENT ON COLUMN users.institutional_email IS 'Contato público da ONG (opcional), pode diferir do e-mail de login';
COMMENT ON COLUMN users.is_verified         IS 'Selo de ONG verificada — toda ONG nasce com ele (sem etapa de aprovação)';
COMMENT ON COLUMN users.instagram           IS 'Nome de usuário, sem @';
COMMENT ON COLUMN users.twitter             IS 'Nome de usuário do X/Twitter, sem @';
COMMENT ON COLUMN users.facebook            IS 'Link completo da página';
COMMENT ON COLUMN users.founded_year        IS 'Ano de fundação da ONG (opcional) — "Fundada em [ano]" no perfil; nulo para usuário comum';
COMMENT ON COLUMN users.cep                 IS 'Apenas os 8 dígitos';
COMMENT ON COLUMN users.state               IS 'Sigla da UF, ex: SC';

-- ------------------------------------------------------------
-- ngo_team — "Equipe" do perfil de ONG (seção 6.2)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ngo_team (
    user_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sort_order  INTEGER       NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    role        VARCHAR(100),

    PRIMARY KEY (user_id, sort_order)
);

COMMENT ON TABLE ngo_team IS 'Integrantes da equipe exibidos no perfil público da ONG, na ordem definida por "sort_order"';

-- ------------------------------------------------------------
-- ngo_visiting_hours — "Horário de visitas" (seção 6.2)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ngo_visiting_hours (
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sort_order  INTEGER      NOT NULL,
    days        VARCHAR(80)  NOT NULL,
    hours       VARCHAR(80)  NOT NULL,

    PRIMARY KEY (user_id, sort_order)
);

COMMENT ON TABLE ngo_visiting_hours IS 'Faixas de horário de visita da ONG (ex: "Terça a sexta" / "14h às 18h")';

-- ------------------------------------------------------------
-- animals — anúncios de adoção (ONG ou usuário comum)
-- Cidade/UF não ficam aqui: o animal está onde o dono está, então
-- a localização vem de users via owner_id.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS animals (
    id                   BIGSERIAL     PRIMARY KEY,
    owner_id             BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- Dados básicos
    name                 VARCHAR(60)   NOT NULL,
    species              VARCHAR(20)   NOT NULL,
    breed                VARCHAR(60)   NOT NULL,
    sex                  VARCHAR(10)   NOT NULL,
    age_value            INTEGER       NOT NULL,
    age_unit             VARCHAR(10)   NOT NULL,
    age_group            VARCHAR(10)   NOT NULL,
    size                 VARCHAR(10)   NOT NULL,

    -- Saúde
    vaccinated           BOOLEAN       NOT NULL DEFAULT FALSE,
    neutered             BOOLEAN       NOT NULL DEFAULT FALSE,
    dewormed             BOOLEAN       NOT NULL DEFAULT FALSE,
    special_needs        BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Comportamento e compatibilidade
    energy_level         VARCHAR(10)   NOT NULL,
    temperament          VARCHAR(20)   NOT NULL,
    independence_level   VARCHAR(10)   NOT NULL,
    vocalization         VARCHAR(10)   NOT NULL,
    good_with_children   BOOLEAN       NOT NULL,
    good_with_dogs       BOOLEAN       NOT NULL,
    good_with_cats       BOOLEAN       NOT NULL,
    apartment_friendly   BOOLEAN       NOT NULL,

    -- Anúncio
    summary              VARCHAR(200)  NOT NULL,
    story                TEXT          NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_animals_species      CHECK (species IN ('DOG', 'CAT', 'OTHER')),
    CONSTRAINT chk_animals_sex          CHECK (sex IN ('MALE', 'FEMALE')),
    CONSTRAINT chk_animals_size         CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT chk_animals_age_unit     CHECK (age_unit IN ('YEARS', 'MONTHS')),
    CONSTRAINT chk_animals_age          CHECK ((age_unit = 'MONTHS' AND age_value BETWEEN 0 AND 11)
                                            OR (age_unit = 'YEARS'  AND age_value BETWEEN 1 AND 30)),
    CONSTRAINT chk_animals_age_group    CHECK (age_group IN ('PUPPY', 'ADULT', 'SENIOR')),
    CONSTRAINT chk_animals_levels       CHECK (energy_level       IN ('LOW', 'MODERATE', 'HIGH')
                                           AND independence_level IN ('LOW', 'MODERATE', 'HIGH')
                                           AND vocalization       IN ('LOW', 'MODERATE', 'HIGH')),
    CONSTRAINT chk_animals_temperament  CHECK (temperament IN ('CALM', 'PLAYFUL', 'AFFECTIONATE', 'PROTECTIVE', 'INDEPENDENT')),
    CONSTRAINT chk_animals_status       CHECK (status IN ('AVAILABLE', 'ADOPTED', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_animals_status_created ON animals (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_animals_owner          ON animals (owner_id);

COMMENT ON TABLE  animals            IS 'Animais anunciados para adoção por ONGs e usuários comuns';
COMMENT ON COLUMN animals.owner_id   IS 'Quem anunciou — a cidade/UF exibidas no anúncio são as do perfil do dono';
COMMENT ON COLUMN animals.age_value  IS 'Idade informada no cadastro, na unidade de age_unit (0-11 meses ou 1-30 anos)';
COMMENT ON COLUMN animals.age_group  IS 'Calculada pela API ao salvar: < 12 meses PUPPY, < 8 anos ADULT, senão SENIOR';
COMMENT ON COLUMN animals.summary    IS 'Frase curta exibida no card da listagem';
COMMENT ON COLUMN animals.status     IS 'AVAILABLE, ADOPTED ou INACTIVE (retirado do ar pelo dono — exclusão lógica)';

-- ------------------------------------------------------------
-- animal_photos — até 4 fotos por animal, na ordem de exibição
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS animal_photos (
    animal_id   BIGINT   NOT NULL REFERENCES animals (id) ON DELETE CASCADE,
    sort_order  INTEGER  NOT NULL,
    url         TEXT     NOT NULL,

    PRIMARY KEY (animal_id, sort_order)
);

COMMENT ON TABLE  animal_photos      IS 'Fotos do anúncio; sort_order 0 é a capa usada no card da listagem';
COMMENT ON COLUMN animal_photos.url  IS 'URL da imagem ou data URL comprimida no frontend (enquanto não há upload próprio)';

-- ------------------------------------------------------------
-- favorites — animais salvos por cada usuário
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS favorites (
    id          BIGSERIAL  PRIMARY KEY,
    user_id     BIGINT     NOT NULL REFERENCES users (id)   ON DELETE CASCADE,
    animal_id   BIGINT     NOT NULL REFERENCES animals (id) ON DELETE CASCADE,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_favorites_user_animal UNIQUE (user_id, animal_id)
);

CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites (user_id, created_at DESC);

COMMENT ON TABLE favorites IS 'Animais favoritados pelos usuários — um registro por par usuário/animal';

-- ------------------------------------------------------------
-- events — eventos criados por ONGs (mesma definição da migração 004)
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
-- event_attendances — confirmações de presença
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event_attendances (
    event_id      BIGINT     NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id       BIGINT     NOT NULL REFERENCES users(id),
    confirmed_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_event_attendances_user ON event_attendances (user_id);

-- ------------------------------------------------------------
-- match_profiles — respostas do quiz do AUmatch (uma linha por
-- pessoa). As colunas aceitam nulo: "Minha conta" salva respostas
-- soltas, e o quiz só conta como completo com as 7 respondidas.
-- A pergunta "Quem mora com você?" vira as três colunas has_*:
-- todas nulas (sem resposta) ou todas preenchidas ("Ninguém" =
-- três FALSE).
-- ------------------------------------------------------------
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

-- ============================================================
-- Opcional: trazer as contas de teste do banco antigo (adocao_db)
-- Rode no terminal, depois deste script:
--   pg_dump -U postgres -h localhost --data-only --table=usuarios adocao_db | psql -U postgres -h localhost -d aucolher_db
-- e em seguida, dentro do aucolher_db:
--   SELECT setval('usuarios_id_seq', (SELECT COALESCE(MAX(id), 1) FROM usuarios));
-- O adocao_db tem colunas em português: faça a importação num banco ainda na
-- versão em português deste script e só depois aplique sql/migrations/.
-- ============================================================
