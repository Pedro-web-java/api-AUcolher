-- ============================================================
-- Migração 013 — exclusão de conta
--
-- A pessoa ou a ONG exclui a própria conta. A linha de users não é
-- apagada: pedidos, avaliações, doações e mensagens das outras pessoas
-- apontam para ela. A conta é anonimizada ("Conta excluída", e-mail
-- excluida-{id}@aucolher.invalid, senha, CNPJ, foto e endereço nulos),
-- fica active = false e ganha deleted_at. Nunca é reativada.
--
-- Por isso as CHECKs que exigem CNPJ (ONG) e senha (conta LOCAL)
-- passam a valer só para contas não excluídas. A CHECK de
-- adoption_requests.cancel_reason não muda: a 014 já aceita
-- ACCOUNT_DELETED.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/013_account_deletion.sql
--
-- Nenhum dado muda. Rodar de novo não estraga nada (IF NOT EXISTS /
-- DROP CONSTRAINT IF EXISTS antes de recriar).
-- ============================================================

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_ngo_has_cnpj;
ALTER TABLE users ADD CONSTRAINT chk_users_ngo_has_cnpj
    CHECK (user_type <> 'NGO' OR cnpj IS NOT NULL OR deleted_at IS NOT NULL);

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_local_has_password;
ALTER TABLE users ADD CONSTRAINT chk_users_local_has_password
    CHECK (provider <> 'LOCAL' OR password IS NOT NULL OR deleted_at IS NOT NULL);

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_deleted_inactive;
ALTER TABLE users ADD CONSTRAINT chk_users_deleted_inactive
    CHECK (deleted_at IS NULL OR active = FALSE);

COMMENT ON COLUMN users.deleted_at IS 'Conta excluída pela própria pessoa (anonimizada, active = false); nunca é reativada';
COMMENT ON COLUMN adoption_requests.cancel_reason IS 'Só em CANCELLED: ADOPTER_WITHDREW, OWNER_WITHDREW, ANIMAL_ADOPTED (outro pedido concluiu), ANIMAL_UNAVAILABLE (o dono tirou o animal do ar), ACCOUNT_DELETED (uma das partes excluiu a conta), ACCOUNT_BANNED (uma das partes foi banida) ou LISTING_REMOVED (anúncio removido pela moderação)';

COMMIT;
