-- ============================================================
-- Migração 005 — pedidos de adoção e avaliações
--
-- Cria adoption_requests (os pedidos, com o fluxo de status
-- validado pela API) e adoption_reviews (as avaliações depois de
-- uma adoção concluída). Até aqui os dois existiam só no navegador
-- (localStorage do frontend).
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/005_adoption_requests.sql
--
-- Roda numa transação só: se algo falhar, nada muda. Rodar de novo
-- não estraga nada (IF NOT EXISTS).
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- adoption_requests — pedidos de adoção (curtir no AUmatch ou
-- "Enviar pedido" na página do animal). O dono não é coluna: é o
-- dono do animal (animals.owner_id).
-- Fluxo: PENDING → ACCEPTED → AWAITING_DELIVERY → CONCLUDED, ou
-- REJECTED pelo dono, ou CANCELLED (por uma das partes ou pelo
-- sistema, com o motivo em cancel_reason).
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

    CONSTRAINT chk_adoption_requests_status         CHECK (status IN ('PENDING', 'ACCEPTED', 'AWAITING_DELIVERY',
                                                                      'CONCLUDED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_adoption_requests_cancel_reason  CHECK (cancel_reason IN ('ADOPTER_WITHDREW', 'OWNER_WITHDREW',
                                                                             'ANIMAL_ADOPTED', 'ANIMAL_UNAVAILABLE')),
    CONSTRAINT chk_adoption_requests_cancelled      CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL)),
    CONSTRAINT chk_adoption_requests_cancelled_from CHECK (cancelled_from_status IS NULL
                                                        OR cancelled_from_status IN ('PENDING', 'ACCEPTED', 'AWAITING_DELIVERY')),
    CONSTRAINT chk_adoption_requests_concluded      CHECK ((status = 'CONCLUDED') = (concluded_at IS NOT NULL))
);

-- Um pedido "vivo" por pessoa e animal: recusado é definitivo, e só um
-- pedido cancelado libera um novo
CREATE UNIQUE INDEX IF NOT EXISTS uq_adoption_requests_open ON adoption_requests (adopter_id, animal_id)
    WHERE status <> 'CANCELLED';
CREATE INDEX IF NOT EXISTS idx_adoption_requests_animal  ON adoption_requests (animal_id, status);
CREATE INDEX IF NOT EXISTS idx_adoption_requests_adopter ON adoption_requests (adopter_id, created_at DESC);

COMMENT ON TABLE  adoption_requests                       IS 'Pedidos de adoção: quem quer adotar (adopter_id) pede um animal ao dono dele';
COMMENT ON COLUMN adoption_requests.status                IS 'PENDING, ACCEPTED, AWAITING_DELIVERY, CONCLUDED, REJECTED ou CANCELLED';
COMMENT ON COLUMN adoption_requests.cancel_reason         IS 'Só em CANCELLED: ADOPTER_WITHDREW, OWNER_WITHDREW, ANIMAL_ADOPTED (outro pedido concluiu) ou ANIMAL_UNAVAILABLE (o dono tirou o animal do ar)';
COMMENT ON COLUMN adoption_requests.cancelled_from_status IS 'Status em que o pedido estava quando foi cancelado';
COMMENT ON COLUMN adoption_requests.closed_at             IS 'Quando o pedido foi recusado ou cancelado';

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

COMMIT;
