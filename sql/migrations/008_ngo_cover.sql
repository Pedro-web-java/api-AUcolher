-- ============================================================
-- Migração 008 — banner do perfil da ONG
--
-- 1. users.cover_url: banner do perfil público, só para ONG.
-- 2. Limpeza: apaga a antiga coluna telefone_whatsapp, se o banco
--    ainda veio do adocao_db (num banco novo, não faz nada). O contato
--    acontece pelo chat do pedido e pelas redes da ONG.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/008_ngo_cover.sql
--
-- Roda numa transação só: se algo falhar, nada muda. Rodar de novo
-- não estraga nada.
-- ============================================================

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS cover_url TEXT;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_cover_only_ngo;
ALTER TABLE users ADD CONSTRAINT chk_users_cover_only_ngo CHECK (cover_url IS NULL OR user_type = 'NGO');

COMMENT ON COLUMN users.cover_url IS 'Banner do perfil público — só ONG; URL ou data URL comprimida no frontend (até 600.000 caracteres)';

ALTER TABLE users DROP COLUMN IF EXISTS telefone_whatsapp;

COMMIT;
