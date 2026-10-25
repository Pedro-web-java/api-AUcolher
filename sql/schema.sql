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
-- VisitingHour, Animal, Favorite, Campaign, Donation) e a seção 6
-- de docs/regras-de-negocio.md.
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
-- campaigns — campanhas de arrecadação das ONGs (migração 007)
-- "Encerrada por prazo" não é gravada: é calculada na leitura
-- (status ACTIVE com deadline < hoje).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS campaigns (
                                         id                    BIGSERIAL     PRIMARY KEY,
                                         ngo_id                BIGINT        NOT NULL REFERENCES users (id),
    title                 VARCHAR(120)  NOT NULL,
    description           TEXT          NOT NULL,
    category              VARCHAR(20)   NOT NULL,
    custom_category       VARCHAR(30),
    is_urgent             BOOLEAN       NOT NULL DEFAULT FALSE,
    goal_amount           NUMERIC(12,2) NOT NULL,
    deadline              DATE,
    cover_url             TEXT,
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    deleted               BOOLEAN       NOT NULL DEFAULT FALSE,
    removed_by_moderation BOOLEAN       NOT NULL DEFAULT FALSE,
    moderated_at          TIMESTAMP,
    moderated_by          BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at             TIMESTAMP,

    CONSTRAINT chk_campaigns_category  CHECK (category IN ('HEALTH', 'FOOD', 'INFRASTRUCTURE', 'NEUTERING', 'RESCUE', 'OTHER')),
    CONSTRAINT chk_campaigns_status    CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_campaigns_goal      CHECK (goal_amount >= 50),
    CONSTRAINT chk_campaigns_removed_by_moderation CHECK (NOT removed_by_moderation OR deleted)
    );

CREATE INDEX IF NOT EXISTS idx_campaigns_ngo ON campaigns (ngo_id);

COMMENT ON TABLE  campaigns                       IS 'Campanhas de arrecadação criadas por ONGs; doações via PIX do Mercado Pago';
COMMENT ON COLUMN campaigns.custom_category       IS 'Nome livre da categoria quando category = OTHER; nulo nas demais';
COMMENT ON COLUMN campaigns.status                IS 'ACTIVE ou CLOSED (encerrada pela ONG). Prazo vencido também conta como encerrada, mas é calculado na leitura';
COMMENT ON COLUMN campaigns.deleted               IS 'Exclusão lógica: some das rotas de campanha, mas as doações aprovadas continuam no histórico';
COMMENT ON COLUMN campaigns.removed_by_moderation IS 'Removida pelo admin (sempre com deleted = true)';
COMMENT ON COLUMN campaigns.moderated_at          IS 'Última edição da campanha feita pelo admin';
COMMENT ON COLUMN campaigns.moderated_by          IS 'O admin que fez a última edição da campanha';

-- ------------------------------------------------------------
-- donations — doações por PIX (Mercado Pago, token da ONG dona)
-- raisedAmount / donationsCount da campanha = SUM / COUNT das APPROVED.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS donations (
                                         id              BIGSERIAL     PRIMARY KEY,
                                         campaign_id     BIGINT        REFERENCES campaigns (id) ON DELETE SET NULL,
    campaign_title  VARCHAR(120)  NOT NULL,
    ngo_id          BIGINT        NOT NULL REFERENCES users (id),
    donor_id        BIGINT        NOT NULL REFERENCES users (id),
    amount          NUMERIC(12,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    mp_order_id     VARCHAR(64),
    pix_qr_code     TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP     NOT NULL,
    approved_at     TIMESTAMP,

    CONSTRAINT uq_donations_mp_order_id UNIQUE (mp_order_id),
    CONSTRAINT chk_donations_amount     CHECK (amount >= 5),
    CONSTRAINT chk_donations_status     CHECK (status IN ('PENDING', 'APPROVED', 'EXPIRED', 'CANCELLED'))
    );

CREATE INDEX IF NOT EXISTS idx_donations_campaign_status ON donations (campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_donations_donor           ON donations (donor_id);
CREATE INDEX IF NOT EXISTS idx_donations_ngo_status      ON donations (ngo_id, status);

COMMENT ON TABLE  donations                IS 'Doações PIX às campanhas. PENDING vale 30 min; depois vira EXPIRED (calculado na leitura)';
COMMENT ON COLUMN donations.campaign_title IS 'Título no momento da doação: o histórico continua legível se a campanha for apagada de vez';
COMMENT ON COLUMN donations.ngo_id         IS 'ONG dona no momento da doação: diz qual token usar para consultar a order, mesmo se a campanha for apagada de vez';

-- ============================================================
-- Opcional: trazer as contas de teste do banco antigo (adocao_db)
-- Rode no terminal, depois deste script:
--   pg_dump -U postgres -h localhost --data-only --table=usuarios adocao_db | psql -U postgres -h localhost -d aucolher_db
-- e em seguida, dentro do aucolher_db:
--   SELECT setval('usuarios_id_seq', (SELECT COALESCE(MAX(id), 1) FROM usuarios));
-- O adocao_db tem colunas em português: faça a importação num banco ainda na
-- versão em português deste script e só depois aplique sql/migrations/.
-- ============================================================