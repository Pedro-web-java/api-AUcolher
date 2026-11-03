-- ============================================================
-- Migração 003 — conta ADMIN e ONGs verificadas
--
-- 1. user_type passa a aceitar 'ADMIN'. Não há cadastro público de
--    admin: a primeira conta é criada pela própria API ao subir, a
--    partir das variáveis ADMIN_EMAIL e ADMIN_PASSWORD (ver README).
-- 2. Toda ONG é verificada (seção 7 das regras de negócio, sem etapa
--    de aprovação): marca as ONGs que já existem — as novas já nascem
--    com o selo.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/003_admin_and_verified_ngos.sql
--
-- Roda numa transação só: se algo falhar, nada muda. Rodar de novo
-- não estraga nada.
-- ============================================================

BEGIN;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_user_type;
ALTER TABLE users ADD CONSTRAINT chk_users_user_type CHECK (user_type IN ('NGO', 'PERSON', 'ADMIN'));

UPDATE users SET is_verified = TRUE WHERE user_type = 'NGO';

COMMENT ON TABLE  users             IS 'Contas da plataforma: ONGs/abrigos, usuários comuns e administradores';
COMMENT ON COLUMN users.is_verified IS 'Selo de ONG verificada — toda ONG nasce com ele (sem etapa de aprovação)';

COMMIT;
