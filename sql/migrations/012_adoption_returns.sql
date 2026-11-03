-- ============================================================
-- Migração 012 — retorno, repasse e score congelado
--
-- Uma adoção concluída pode voltar para quem doou (RETURNED) ou ser
-- republicada pelo adotante (REHOMED). Os dois contam na taxa de
-- reabandono do painel do admin. O score do AUmatch fica congelado na
-- conclusão (match_score_at_adoption); os pedidos concluídos antes desta
-- migração ficam com ele nulo.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/012_adoption_returns.sql
--
-- Roda numa transação só: se algo falhar, nada muda. Rodar de novo
-- não estraga nada (IF NOT EXISTS / DROP CONSTRAINT IF EXISTS).
-- ============================================================

BEGIN;

ALTER TABLE adoption_requests
    ADD COLUMN IF NOT EXISTS match_score_at_adoption SMALLINT,
    ADD COLUMN IF NOT EXISTS return_date             DATE,
    ADD COLUMN IF NOT EXISTS return_reason           VARCHAR(30),
    ADD COLUMN IF NOT EXISTS return_note             VARCHAR(500),
    ADD COLUMN IF NOT EXISTS return_recorded_at      TIMESTAMP;

-- Status: os de antes + RETURNED e REHOMED
ALTER TABLE adoption_requests DROP CONSTRAINT IF EXISTS chk_adoption_requests_status;
ALTER TABLE adoption_requests ADD CONSTRAINT chk_adoption_requests_status
    CHECK (status IN ('PENDING', 'ACCEPTED', 'AWAITING_DELIVERY', 'CONCLUDED', 'REJECTED', 'CANCELLED',
                      'RETURNED', 'REHOMED'));

-- concluded_at: preenchido em toda adoção que aconteceu (a 005 exigia só em CONCLUDED)
ALTER TABLE adoption_requests DROP CONSTRAINT IF EXISTS chk_adoption_requests_concluded;
ALTER TABLE adoption_requests ADD CONSTRAINT chk_adoption_requests_concluded
    CHECK ((status IN ('CONCLUDED', 'RETURNED', 'REHOMED')) = (concluded_at IS NOT NULL));

ALTER TABLE adoption_requests DROP CONSTRAINT IF EXISTS chk_adoption_requests_score;
ALTER TABLE adoption_requests ADD CONSTRAINT chk_adoption_requests_score
    CHECK (match_score_at_adoption IS NULL OR match_score_at_adoption BETWEEN 0 AND 100);

ALTER TABLE adoption_requests DROP CONSTRAINT IF EXISTS chk_adoption_requests_return_reason;
ALTER TABLE adoption_requests ADD CONSTRAINT chk_adoption_requests_return_reason
    CHECK (return_reason IS NULL OR return_reason IN
        ('BEHAVIOR', 'OTHER_PETS', 'CHILDREN', 'HOUSING_OR_ROUTINE', 'COSTS', 'ANIMAL_HEALTH', 'OTHER'));

-- Os dados do retorno existem exatamente em RETURNED e REHOMED
ALTER TABLE adoption_requests DROP CONSTRAINT IF EXISTS chk_adoption_requests_returned;
ALTER TABLE adoption_requests ADD CONSTRAINT chk_adoption_requests_returned
    CHECK ((status IN ('RETURNED', 'REHOMED')) =
           (return_date IS NOT NULL AND return_reason IS NOT NULL AND return_recorded_at IS NOT NULL));

ALTER TABLE adoption_requests DROP CONSTRAINT IF EXISTS chk_adoption_requests_return_note;
ALTER TABLE adoption_requests ADD CONSTRAINT chk_adoption_requests_return_note
    CHECK (return_reason IS DISTINCT FROM 'OTHER' OR (return_note IS NOT NULL AND char_length(btrim(return_note)) > 0));

COMMENT ON COLUMN adoption_requests.match_score_at_adoption IS 'Score do AUmatch congelado na conclusão (nulo sem quiz completo) — base das faixas da taxa de reabandono';
COMMENT ON COLUMN adoption_requests.return_date             IS 'RETURNED: data em que o animal voltou ao doador; REHOMED: data da republicação';
COMMENT ON COLUMN adoption_requests.return_reason           IS 'Motivo do retorno ou do repasse';
COMMENT ON COLUMN adoption_requests.return_note             IS 'Observação do retorno ou do repasse (obrigatória no motivo OTHER)';
COMMENT ON COLUMN adoption_requests.return_recorded_at      IS 'Quando o retorno ou o repasse foi registrado (ou corrigido)';

-- O anúncio republicado aponta para a adoção de onde veio (uma republicação por adoção)
ALTER TABLE animals ADD COLUMN IF NOT EXISTS origin_request_id BIGINT
    REFERENCES adoption_requests (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_animals_origin_request ON animals (origin_request_id)
    WHERE origin_request_id IS NOT NULL;

COMMENT ON COLUMN animals.origin_request_id IS 'Anúncio republicado pelo adotante: o pedido de adoção de onde ele veio (nulo nos demais)';

-- Taxa de reabandono no painel do admin
CREATE INDEX IF NOT EXISTS idx_adoption_requests_outcome ON adoption_requests (status)
    WHERE status IN ('CONCLUDED', 'RETURNED', 'REHOMED');

COMMIT;
