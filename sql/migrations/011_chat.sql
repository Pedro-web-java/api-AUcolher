-- ============================================================
-- Migração 011 — chat dos pedidos de adoção
--
-- Cria chat_messages (mensagens de pessoa e avisos de sistema de
-- cada pedido) e chat_reads (até onde cada parte já leu).
--
-- psql -U postgres -h localhost -d aucolher_db -f sql/migrations/011_chat.sql
--
-- Roda numa transação só e pode ser repetida (IF NOT EXISTS).
-- ============================================================
BEGIN;

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
    CONSTRAINT chk_chat_messages_shape        CHECK ((type = 'USER'   AND sender_id IS NOT NULL AND text IS NOT NULL AND system_event IS NULL)
                                                  OR (type = 'SYSTEM' AND sender_id IS NULL     AND text IS NULL     AND system_event IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_request ON chat_messages (request_id, id);

COMMENT ON TABLE  chat_messages              IS 'Mensagens do chat de cada pedido de adoção (o pedido é a conversa)';
COMMENT ON COLUMN chat_messages.sender_id    IS 'Quem escreveu (adotante ou dono) — nulo nas mensagens de sistema';
COMMENT ON COLUMN chat_messages.type         IS 'USER (escrita por uma das partes) ou SYSTEM (aviso criado pela API na troca de status)';
COMMENT ON COLUMN chat_messages.text         IS 'Texto da mensagem USER, de 1 a 1000 caracteres — nulo em SYSTEM';
COMMENT ON COLUMN chat_messages.system_event IS 'Só em SYSTEM: DELIVERY_REQUESTED, DELIVERY_DECLINED, ADOPTION_CONCLUDED, ADOPTER_WITHDREW, OWNER_WITHDREW, ANIMAL_RETURNED ou ANIMAL_REHOMED';

CREATE TABLE IF NOT EXISTS chat_reads (
    request_id            BIGINT     NOT NULL REFERENCES adoption_requests (id) ON DELETE CASCADE,
    user_id               BIGINT     NOT NULL REFERENCES users (id)             ON DELETE CASCADE,
    last_read_message_id  BIGINT     NOT NULL CHECK (last_read_message_id >= 0),
    read_at               TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (request_id, user_id)
);

COMMENT ON TABLE  chat_reads                      IS 'Marca de leitura de cada parte em cada conversa — alimenta o contador de não lidas';
COMMENT ON COLUMN chat_reads.last_read_message_id IS 'Última mensagem vista; só aumenta (GREATEST no upsert). Não é FK de propósito: é só um marcador';

COMMIT;
