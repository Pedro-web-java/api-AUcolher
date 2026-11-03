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
-- Banco já criado pela versão anterior em inglês (sem as
-- migrações 009 a 011)? Também não rode este: aplique só
--   009_mercado_pago_accounts.sql  (conta Mercado Pago da ONG)
--   010_password_security.sql      (recuperar/alterar senha)
--   011_chat.sql                   (chat dos pedidos de adoção)
--
-- Espelha as entidades do Spring Boot (User, TeamMember,
-- VisitingHour, Animal, Favorite, MatchProfile, AdoptionRequest,
-- AdoptionReview, ChatMessage, ChatRead, Event, EventAttendance,
-- Report, Campaign, Donation, MercadoPagoAccount,
-- PasswordResetToken) e a seção 6 de
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
    password_changed_at  TIMESTAMP,
    user_type            VARCHAR(20)   NOT NULL,
    provider             VARCHAR(20)   NOT NULL DEFAULT 'LOCAL',
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    moderated_at         TIMESTAMP,
    moderated_by         BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    deleted_at           TIMESTAMP,

    -- Perfil (comum e ONG)
    photo_url            TEXT,
    cover_url            TEXT,
    bio                  TEXT,

    -- Perfil de ONG
    cnpj                 VARCHAR(14),
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
    -- Conta excluída é anonimizada: perde o CNPJ e a senha
    CONSTRAINT chk_users_ngo_has_cnpj        CHECK (user_type <> 'NGO' OR cnpj IS NOT NULL OR deleted_at IS NOT NULL),
    CONSTRAINT chk_users_local_has_password  CHECK (provider <> 'LOCAL' OR password IS NOT NULL OR deleted_at IS NOT NULL),
    CONSTRAINT chk_users_deleted_inactive    CHECK (deleted_at IS NULL OR active = FALSE),
    CONSTRAINT chk_users_founded_year        CHECK (founded_year IS NULL OR (user_type = 'NGO' AND founded_year >= 1800)),
    CONSTRAINT chk_users_cover_only_ngo      CHECK (cover_url IS NULL OR user_type = 'NGO')
);

CREATE INDEX IF NOT EXISTS idx_users_user_type ON users (user_type);

COMMENT ON TABLE  users                     IS 'Contas da plataforma: ONGs/abrigos, usuários comuns e administradores';
COMMENT ON COLUMN users.password            IS 'Hash BCrypt — nulo para contas criadas via OAuth2 (Google) que ainda não criaram senha; conta do Google que define senha entra pelos dois caminhos (provider continua GOOGLE)';
COMMENT ON COLUMN users.password_changed_at IS 'Última troca ou redefinição de senha; tokens JWT com iat anterior são recusados (derruba as sessões)';
COMMENT ON COLUMN users.active              IS 'false = conta suspensa pela moderação (ou excluída, com deleted_at): não entra e o conteúdo some das rotas públicas';
COMMENT ON COLUMN users.moderated_at        IS 'Última edição do perfil feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN users.moderated_by        IS 'O admin que fez a última edição do perfil';
COMMENT ON COLUMN users.deleted_at          IS 'Conta excluída pela própria pessoa (anonimizada, active = false); nunca é reativada';
COMMENT ON COLUMN users.photo_url           IS 'URL do avatar ou data URL da imagem comprimida no frontend (enquanto não há upload próprio)';
COMMENT ON COLUMN users.cover_url           IS 'Banner do perfil público — só ONG; URL ou data URL comprimida no frontend (até 600.000 caracteres)';
COMMENT ON COLUMN users.bio                 IS 'Bio do usuário comum (cuidadores autônomos) ou descrição da ONG';
COMMENT ON COLUMN users.cnpj                IS 'Apenas os 14 dígitos — obrigatório quando user_type = NGO';
COMMENT ON COLUMN users.is_verified         IS 'Selo de ONG verificada — toda ONG nasce com ele (sem etapa de aprovação)';
COMMENT ON COLUMN users.instagram           IS 'Nome de usuário, sem @';
COMMENT ON COLUMN users.twitter             IS 'Nome de usuário do X/Twitter, sem @';
COMMENT ON COLUMN users.facebook            IS 'Link completo da página';
COMMENT ON COLUMN users.founded_year        IS 'Ano de fundação da ONG (opcional) — "Fundada em [ano]" no perfil; nulo para usuário comum';
COMMENT ON COLUMN users.cep                 IS 'Apenas os 8 dígitos';
COMMENT ON COLUMN users.state               IS 'Sigla da UF, ex: SC';

-- ------------------------------------------------------------
-- password_reset_tokens — links de "Esqueci minha senha" e de
-- "Alterar senha"/"Criar senha" em Minha conta. Só o hash
-- (SHA-256) do token fica no banco; uso único; vale 30 minutos.
-- Pedir um link novo marca os anteriores não usados como usados
-- (used_at). Limite de 1 e-mail por minuto por conta (created_at).
-- ------------------------------------------------------------
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
    moderated_at         TIMESTAMP,
    moderated_by         BIGINT        REFERENCES users (id) ON DELETE SET NULL,

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
    CONSTRAINT chk_animals_status       CHECK (status IN ('AVAILABLE', 'ADOPTED', 'INACTIVE', 'REMOVED'))
);

CREATE INDEX IF NOT EXISTS idx_animals_status_created ON animals (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_animals_owner          ON animals (owner_id);

COMMENT ON TABLE  animals            IS 'Animais anunciados para adoção por ONGs e usuários comuns';
COMMENT ON COLUMN animals.owner_id   IS 'Quem anunciou — a cidade/UF exibidas no anúncio são as do perfil do dono';
COMMENT ON COLUMN animals.age_value  IS 'Idade informada no cadastro, na unidade de age_unit (0-11 meses ou 1-30 anos)';
COMMENT ON COLUMN animals.age_group  IS 'Calculada pela API ao salvar: < 12 meses PUPPY, < 8 anos ADULT, senão SENIOR';
COMMENT ON COLUMN animals.summary    IS 'Frase curta exibida no card da listagem';
COMMENT ON COLUMN animals.status     IS 'AVAILABLE, ADOPTED, INACTIVE (retirado do ar pelo dono — exclusão lógica) ou REMOVED (removido pela moderação)';

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

-- ------------------------------------------------------------
-- adoption_requests — pedidos de adoção (curtir no AUmatch ou
-- "Enviar pedido" na página do animal). O dono não é coluna: é o
-- dono do animal (animals.owner_id).
-- Fluxo: PENDING → ACCEPTED → AWAITING_DELIVERY → CONCLUDED, ou
-- REJECTED pelo dono, ou CANCELLED (por uma das partes ou pelo
-- sistema, com o motivo em cancel_reason). Depois de CONCLUDED, o
-- animal pode voltar ao doador (RETURNED) ou ser republicado pelo
-- adotante (REHOMED) — os dois contam na taxa de reabandono.
-- O pedido também é a conversa do chat (ver chat_messages).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS adoption_requests (
    id                     BIGSERIAL    PRIMARY KEY,
    animal_id              BIGINT       NOT NULL REFERENCES animals (id) ON DELETE CASCADE,
    adopter_id             BIGINT       NOT NULL REFERENCES users (id)   ON DELETE CASCADE,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    cancel_reason          VARCHAR(30),
    cancelled_from_status  VARCHAR(20),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concluded_at           TIMESTAMP,
    closed_at              TIMESTAMP,

    -- Retorno ao doador (RETURNED) ou repasse pelo adotante (REHOMED)
    match_score_at_adoption SMALLINT,
    return_date            DATE,
    return_reason          VARCHAR(30),
    return_note            VARCHAR(500),
    return_recorded_at     TIMESTAMP,

    CONSTRAINT chk_adoption_requests_status         CHECK (status IN ('PENDING', 'ACCEPTED', 'AWAITING_DELIVERY',
                                                                      'CONCLUDED', 'REJECTED', 'CANCELLED',
                                                                      'RETURNED', 'REHOMED')),
    CONSTRAINT chk_adoption_requests_cancel_reason  CHECK (cancel_reason IN ('ADOPTER_WITHDREW', 'OWNER_WITHDREW',
                                                                             'ANIMAL_ADOPTED', 'ANIMAL_UNAVAILABLE',
                                                                             'ACCOUNT_DELETED', 'ACCOUNT_BANNED',
                                                                             'LISTING_REMOVED')),
    CONSTRAINT chk_adoption_requests_cancelled      CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL)),
    CONSTRAINT chk_adoption_requests_cancelled_from CHECK (cancelled_from_status IS NULL
                                                        OR cancelled_from_status IN ('PENDING', 'ACCEPTED', 'AWAITING_DELIVERY')),
    CONSTRAINT chk_adoption_requests_concluded      CHECK ((status IN ('CONCLUDED', 'RETURNED', 'REHOMED')) = (concluded_at IS NOT NULL)),
    CONSTRAINT chk_adoption_requests_score          CHECK (match_score_at_adoption IS NULL OR match_score_at_adoption BETWEEN 0 AND 100),
    CONSTRAINT chk_adoption_requests_return_reason  CHECK (return_reason IS NULL OR return_reason IN
                                                        ('BEHAVIOR', 'OTHER_PETS', 'CHILDREN', 'HOUSING_OR_ROUTINE',
                                                         'COSTS', 'ANIMAL_HEALTH', 'OTHER')),
    CONSTRAINT chk_adoption_requests_returned       CHECK ((status IN ('RETURNED', 'REHOMED')) =
                                                        (return_date IS NOT NULL AND return_reason IS NOT NULL
                                                         AND return_recorded_at IS NOT NULL)),
    CONSTRAINT chk_adoption_requests_return_note    CHECK (return_reason IS DISTINCT FROM 'OTHER'
                                                        OR (return_note IS NOT NULL AND char_length(btrim(return_note)) > 0))
);

-- Um pedido "vivo" por pessoa e animal: recusado é definitivo, e só um
-- pedido cancelado libera um novo
CREATE UNIQUE INDEX IF NOT EXISTS uq_adoption_requests_open ON adoption_requests (adopter_id, animal_id)
    WHERE status <> 'CANCELLED';
CREATE INDEX IF NOT EXISTS idx_adoption_requests_animal  ON adoption_requests (animal_id, status);
CREATE INDEX IF NOT EXISTS idx_adoption_requests_adopter ON adoption_requests (adopter_id, created_at DESC);
-- Taxa de reabandono no painel do admin
CREATE INDEX IF NOT EXISTS idx_adoption_requests_outcome ON adoption_requests (status)
    WHERE status IN ('CONCLUDED', 'RETURNED', 'REHOMED');

COMMENT ON TABLE  adoption_requests                       IS 'Pedidos de adoção: quem quer adotar (adopter_id) pede um animal ao dono dele. O id do pedido é também o id da conversa do chat';
COMMENT ON COLUMN adoption_requests.status                IS 'PENDING, ACCEPTED, AWAITING_DELIVERY, CONCLUDED, REJECTED, CANCELLED, RETURNED (voltou ao doador) ou REHOMED (republicado pelo adotante)';
COMMENT ON COLUMN adoption_requests.cancel_reason         IS 'Só em CANCELLED: ADOPTER_WITHDREW, OWNER_WITHDREW, ANIMAL_ADOPTED (outro pedido concluiu), ANIMAL_UNAVAILABLE (o dono tirou o animal do ar), ACCOUNT_DELETED (uma das partes excluiu a conta), ACCOUNT_BANNED (uma das partes foi banida) ou LISTING_REMOVED (anúncio removido pela moderação)';
COMMENT ON COLUMN adoption_requests.cancelled_from_status IS 'Status em que o pedido estava quando foi cancelado (define se o pedido chegou a ter conversa no chat)';
COMMENT ON COLUMN adoption_requests.closed_at             IS 'Quando o pedido foi recusado ou cancelado';
COMMENT ON COLUMN adoption_requests.match_score_at_adoption IS 'Score do AUmatch congelado na conclusão (nulo sem quiz completo) — base das faixas da taxa de reabandono';
COMMENT ON COLUMN adoption_requests.return_date             IS 'RETURNED: data em que o animal voltou ao doador; REHOMED: data da republicação';
COMMENT ON COLUMN adoption_requests.return_reason           IS 'Motivo do retorno ou do repasse';
COMMENT ON COLUMN adoption_requests.return_note             IS 'Observação do retorno ou do repasse (obrigatória no motivo OTHER)';
COMMENT ON COLUMN adoption_requests.return_recorded_at      IS 'Quando o retorno ou o repasse foi registrado (ou corrigido)';

-- O anúncio republicado pelo adotante aponta para a adoção de onde veio.
-- Fica aqui, e não no CREATE TABLE animals, porque precisa de adoption_requests.
ALTER TABLE animals ADD COLUMN IF NOT EXISTS origin_request_id BIGINT
    REFERENCES adoption_requests (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_animals_origin_request ON animals (origin_request_id)
    WHERE origin_request_id IS NOT NULL;

COMMENT ON COLUMN animals.origin_request_id IS 'Anúncio republicado pelo adotante: o pedido de adoção de onde ele veio (nulo nos demais)';
COMMENT ON COLUMN animals.moderated_at IS 'Última edição do anúncio feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN animals.moderated_by IS 'O admin que fez a última edição do anúncio';

-- ------------------------------------------------------------
-- adoption_reviews — avaliações depois de uma adoção concluída.
-- Uma por lado: ADOPTER avalia o dono, OWNER avalia o adotante.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS adoption_reviews (
    id           BIGSERIAL     PRIMARY KEY,
    request_id   BIGINT        NOT NULL REFERENCES adoption_requests (id) ON DELETE CASCADE,
    author_role  VARCHAR(10)   NOT NULL,
    rating       INTEGER       NOT NULL,
    comment      VARCHAR(500),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP,

    CONSTRAINT chk_adoption_reviews_author_role CHECK (author_role IN ('ADOPTER', 'OWNER')),
    CONSTRAINT chk_adoption_reviews_rating      CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_adoption_reviews_request_role UNIQUE (request_id, author_role)
);

COMMENT ON TABLE  adoption_reviews             IS 'Avaliações de adoções concluídas — uma por lado de cada pedido';
COMMENT ON COLUMN adoption_reviews.author_role IS 'ADOPTER: o adotante avalia o dono; OWNER: o dono avalia o adotante';
COMMENT ON COLUMN adoption_reviews.rating      IS 'Estrelas, de 1 a 5';
COMMENT ON COLUMN adoption_reviews.updated_at  IS 'Preenchida só quando o autor edita a avaliação';

-- ------------------------------------------------------------
-- chat_messages — a conversa de um pedido de adoção. Não existe
-- tabela de conversa: o pedido (adoption_requests) é a conversa.
-- A conversa existe a partir do aceite (ACCEPTED) e só aceita
-- mensagens novas em ACCEPTED (regra aplicada na API).
-- USER: mensagem de uma das partes (sender_id + text).
-- SYSTEM: aviso criado pela API, na mesma transação da troca de
-- status do pedido (system_event); o frontend monta o texto.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    id            BIGSERIAL     PRIMARY KEY,
    request_id    BIGINT        NOT NULL REFERENCES adoption_requests (id) ON DELETE CASCADE,
    sender_id     BIGINT        REFERENCES users (id) ON DELETE CASCADE,
    type          VARCHAR(10)   NOT NULL DEFAULT 'USER',
    text          VARCHAR(1000),
    system_event  VARCHAR(30),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_chat_messages_type         CHECK (type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_chat_messages_system_event CHECK (system_event IN ('DELIVERY_REQUESTED', 'DELIVERY_DECLINED',
                                                                      'ADOPTION_CONCLUDED',
                                                                      'ADOPTER_WITHDREW', 'OWNER_WITHDREW',
                                                                      'ANIMAL_RETURNED', 'ANIMAL_REHOMED')),
    CONSTRAINT chk_chat_messages_text         CHECK (text IS NULL OR char_length(btrim(text)) > 0),
    CONSTRAINT chk_chat_messages_shape        CHECK ((type = 'USER'   AND sender_id IS NOT NULL AND text IS NOT NULL     AND system_event IS NULL)
                                                  OR (type = 'SYSTEM' AND sender_id IS NULL     AND text IS NULL         AND system_event IS NOT NULL))
);

-- Histórico e polling ("mensagens depois do id X") de uma conversa
CREATE INDEX IF NOT EXISTS idx_chat_messages_request ON chat_messages (request_id, id);

COMMENT ON TABLE  chat_messages              IS 'Mensagens do chat de cada pedido de adoção (o pedido é a conversa)';
COMMENT ON COLUMN chat_messages.sender_id    IS 'Quem escreveu (adotante ou dono) — nulo nas mensagens de sistema';
COMMENT ON COLUMN chat_messages.type         IS 'USER (escrita por uma das partes) ou SYSTEM (aviso criado pela API na troca de status)';
COMMENT ON COLUMN chat_messages.text         IS 'Texto da mensagem USER, de 1 a 1000 caracteres — nulo em SYSTEM';
COMMENT ON COLUMN chat_messages.system_event IS 'Só em SYSTEM: DELIVERY_REQUESTED, DELIVERY_DECLINED, ADOPTION_CONCLUDED, ADOPTER_WITHDREW, OWNER_WITHDREW, ANIMAL_RETURNED ou ANIMAL_REHOMED';

-- ------------------------------------------------------------
-- chat_reads — até qual mensagem cada parte já leu, por conversa.
-- Não lidas = mensagens com id maior, que não são da própria conta
-- (mensagens de sistema contam para as duas partes).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_reads (
    request_id            BIGINT     NOT NULL REFERENCES adoption_requests (id) ON DELETE CASCADE,
    user_id               BIGINT     NOT NULL REFERENCES users (id)             ON DELETE CASCADE,
    last_read_message_id  BIGINT     NOT NULL CHECK (last_read_message_id >= 0),
    read_at               TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (request_id, user_id)
);

COMMENT ON TABLE  chat_reads                      IS 'Marca de leitura de cada parte em cada conversa — alimenta o contador de não lidas';
COMMENT ON COLUMN chat_reads.last_read_message_id IS 'Última mensagem vista; só aumenta (GREATEST no upsert). Não é FK de propósito: é só um marcador';

-- ------------------------------------------------------------
-- events — eventos criados por ONGs. Excluir um evento com
-- confirmados só muda o status para CANCELLED.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS events (
    id           BIGSERIAL     PRIMARY KEY,
    ngo_id       BIGINT        NOT NULL REFERENCES users(id),
    title        VARCHAR(120)  NOT NULL,
    description  TEXT          NOT NULL,
    category     VARCHAR(20)   NOT NULL CHECK (category IN ('ADOPTION_FAIR', 'HEALTH', 'BAZAAR', 'WORKSHOP', 'COLLECTION', 'MEETUP', 'OTHER')),
    custom_category VARCHAR(30),
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
    removed_by_moderation BOOLEAN NOT NULL DEFAULT FALSE,
    moderated_at TIMESTAMP,
    moderated_by BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    CHECK (end_time IS NULL OR end_time > start_time),
    CONSTRAINT chk_events_removed_by_moderation CHECK (NOT removed_by_moderation OR status = 'CANCELLED')
);

COMMENT ON COLUMN events.removed_by_moderation IS 'Cancelado pelo admin (sempre com status CANCELLED)';
COMMENT ON COLUMN events.moderated_at  IS 'Última edição do evento feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN events.moderated_by  IS 'O admin que fez a última edição do evento';

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

-- ------------------------------------------------------------
-- mercado_pago_accounts — conta Mercado Pago de cada ONG (OAuth
-- do próprio Mercado Pago). As doações caem direto nela: a
-- plataforma não guarda doações nem cobra comissão. Os tokens
-- ficam criptografados pela aplicação (AES-256-GCM): o banco só
-- vê texto cifrado. Sem conta CONNECTED, a ONG não cria campanha
-- e as campanhas dela passam a acceptingDonations = false
-- (calculado na leitura, não é coluna).
-- Excluir a conta da ONG apaga a linha (ON DELETE CASCADE).
-- ------------------------------------------------------------
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
COMMENT ON COLUMN mercado_pago_accounts.live_mode     IS 'false = credenciais de teste (sandbox)';
COMMENT ON COLUMN mercado_pago_accounts.status        IS 'CONNECTED ou NEEDS_RECONNECT (renovação falhou ou acesso revogado)';

-- ------------------------------------------------------------
-- campaigns — campanhas de arrecadação das ONGs. "Encerrada por
-- prazo" não é gravada: é calculada na leitura (status ACTIVE com
-- deadline < hoje). Excluir com doação aprovada é lógico (deleted).
-- "acceptingDonations" também é calculado na leitura: campanha
-- ACTIVE e ONG com conta Mercado Pago CONNECTED.
-- ------------------------------------------------------------
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

COMMENT ON TABLE  campaigns                       IS 'Campanhas de arrecadação criadas por ONGs; doações via PIX do Mercado Pago, direto na conta da ONG';
COMMENT ON COLUMN campaigns.custom_category       IS 'Nome livre da categoria quando category = OTHER; nulo nas demais';
COMMENT ON COLUMN campaigns.status                IS 'ACTIVE ou CLOSED (encerrada pela ONG). Prazo vencido também conta como encerrada, mas é calculado na leitura';
COMMENT ON COLUMN campaigns.deleted               IS 'Exclusão lógica: some das rotas de campanha, mas as doações aprovadas continuam no histórico';
COMMENT ON COLUMN campaigns.removed_by_moderation IS 'Removida pelo admin (sempre com deleted = true)';
COMMENT ON COLUMN campaigns.moderated_at          IS 'Última edição da campanha feita pelo admin (nulo se nunca houve)';
COMMENT ON COLUMN campaigns.moderated_by          IS 'O admin que fez a última edição da campanha';

-- ------------------------------------------------------------
-- donations — doações por PIX (Mercado Pago, token da ONG dona).
-- raisedAmount / donationsCount da campanha = SUM / COUNT das
-- APPROVED. A aprovação é um UPDATE atômico (PENDING|CANCELLED →
-- APPROVED), para webhook e polling nunca somarem duas vezes.
-- A order sai sem marketplace_fee: a plataforma não cobra comissão.
-- ------------------------------------------------------------
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

COMMENT ON TABLE  donations                IS 'Doações PIX às campanhas. PENDING vale 30 min; depois vira EXPIRED (calculado na leitura)';
COMMENT ON COLUMN donations.campaign_title IS 'Título no momento da doação: o histórico continua legível se a campanha for apagada de vez';
COMMENT ON COLUMN donations.ngo_id         IS 'ONG dona no momento da doação: diz qual token do Mercado Pago (mercado_pago_accounts) consulta a order';

-- ------------------------------------------------------------
-- reports — denúncias feitas no site. O alvo é polimórfico
-- (target_type + target_id, sem chave estrangeira): pode ser um
-- animal ou uma conta. As colunas target_* guardam a cópia do alvo
-- no momento da denúncia, para o admin ver o que foi denunciado
-- mesmo depois de o anúncio sair do ar. Banir uma conta usa
-- users.active (false = suspensa pela moderação).
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

-- ============================================================
-- Opcional: trazer as contas de teste do banco antigo (adocao_db)
-- Rode no terminal, depois deste script:
--   pg_dump -U postgres -h localhost --data-only --table=usuarios adocao_db | psql -U postgres -h localhost -d aucolher_db
-- e em seguida, dentro do aucolher_db:
--   SELECT setval('usuarios_id_seq', (SELECT COALESCE(MAX(id), 1) FROM usuarios));
-- O adocao_db tem colunas em português: faça a importação num banco ainda na
-- versão em português deste script e só depois aplique sql/migrations/.
-- ============================================================
