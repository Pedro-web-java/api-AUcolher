-- ============================================================
-- Migração 015 — remove o e-mail institucional da ONG
--
-- O contato público da ONG passa a ser o próprio e-mail de login: o
-- cadastro aceita qualquer e-mail, então quem tem um institucional já
-- se cadastra com ele. A coluna users.institutional_email sai.
-- (As migrações 007 a 014 estão reservadas no plano das sprints.)
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/015_drop_institutional_email.sql
--
-- Apaga os e-mails institucionais que estiverem gravados. Rodar de novo
-- não estraga nada (IF EXISTS).
-- ============================================================

BEGIN;

ALTER TABLE users DROP COLUMN IF EXISTS institutional_email;

COMMIT;
