-- ============================================================
-- Migração 001 — tabelas de usuário com nomes em inglês
--
-- Para bancos criados pela versão em português do schema.sql: renomeia
-- tabelas, colunas e constraints sem apagar nenhum dado. Banco novo não
-- precisa disto — o schema.sql atual já cria tudo em inglês.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/001_users_english.sql
--
-- Roda numa transação só: se algo falhar, nada muda.
-- ============================================================

BEGIN;

-- Bancos de antes do ano de fundação não têm a coluna; sem ela o RENAME abaixo falha
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS ano_fundacao INTEGER;

-- ------------------------------------------------------------
-- usuarios -> users
-- ------------------------------------------------------------
ALTER TABLE usuarios RENAME TO users;

ALTER TABLE users RENAME COLUMN nome                TO name;
ALTER TABLE users RENAME COLUMN senha               TO password;
ALTER TABLE users RENAME COLUMN tipo_usuario        TO user_type;
ALTER TABLE users RENAME COLUMN ativo               TO active;
ALTER TABLE users RENAME COLUMN data_criacao        TO created_at;
ALTER TABLE users RENAME COLUMN foto_url            TO photo_url;
ALTER TABLE users RENAME COLUMN email_institucional TO institutional_email;
ALTER TABLE users RENAME COLUMN is_verificado       TO is_verified;
ALTER TABLE users RENAME COLUMN ano_fundacao        TO founded_year;
ALTER TABLE users RENAME COLUMN logradouro          TO street;
ALTER TABLE users RENAME COLUMN numero              TO number;
ALTER TABLE users RENAME COLUMN complemento         TO complement;
ALTER TABLE users RENAME COLUMN bairro              TO district;
ALTER TABLE users RENAME COLUMN cidade              TO city;
ALTER TABLE users RENAME COLUMN estado              TO state;

ALTER SEQUENCE usuarios_id_seq RENAME TO users_id_seq;
ALTER INDEX IF EXISTS idx_usuarios_tipo_usuario RENAME TO idx_users_user_type;

ALTER TABLE users RENAME CONSTRAINT usuarios_pkey                   TO users_pkey;
ALTER TABLE users RENAME CONSTRAINT uq_usuarios_email               TO uq_users_email;
ALTER TABLE users RENAME CONSTRAINT uq_usuarios_cnpj                TO uq_users_cnpj;
ALTER TABLE users RENAME CONSTRAINT chk_usuarios_provider           TO chk_users_provider;
ALTER TABLE users RENAME CONSTRAINT chk_usuarios_local_possui_senha TO chk_users_local_has_password;

-- Os CHECKs que citam 'ONG'/'USUARIO_COMUM' saem antes de trocar os valores e voltam com os novos
ALTER TABLE users DROP CONSTRAINT chk_usuarios_tipo_usuario;
ALTER TABLE users DROP CONSTRAINT chk_usuarios_ong_possui_cnpj;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_usuarios_ano_fundacao;

UPDATE users SET user_type = CASE user_type
    WHEN 'ONG'           THEN 'NGO'
    WHEN 'USUARIO_COMUM' THEN 'PERSON'
    ELSE user_type
END;

ALTER TABLE users ADD CONSTRAINT chk_users_user_type    CHECK (user_type IN ('NGO', 'PERSON'));
ALTER TABLE users ADD CONSTRAINT chk_users_ngo_has_cnpj CHECK (user_type <> 'NGO' OR cnpj IS NOT NULL);
ALTER TABLE users ADD CONSTRAINT chk_users_founded_year
    CHECK (founded_year IS NULL OR (user_type = 'NGO' AND founded_year >= 1800));

COMMENT ON COLUMN users.cnpj IS 'Apenas os 14 dígitos — obrigatório quando user_type = NGO';

-- ------------------------------------------------------------
-- ong_equipe -> ngo_team
-- ------------------------------------------------------------
ALTER TABLE ong_equipe RENAME TO ngo_team;

ALTER TABLE ngo_team RENAME COLUMN usuario_id TO user_id;
ALTER TABLE ngo_team RENAME COLUMN ordem      TO sort_order;
ALTER TABLE ngo_team RENAME COLUMN nome       TO name;
ALTER TABLE ngo_team RENAME COLUMN funcao     TO role;

ALTER TABLE ngo_team RENAME CONSTRAINT ong_equipe_pkey            TO ngo_team_pkey;
ALTER TABLE ngo_team RENAME CONSTRAINT ong_equipe_usuario_id_fkey TO ngo_team_user_id_fkey;

COMMENT ON TABLE ngo_team IS 'Integrantes da equipe exibidos no perfil público da ONG, na ordem definida por "sort_order"';

-- ------------------------------------------------------------
-- ong_horarios_visita -> ngo_visiting_hours
-- ------------------------------------------------------------
ALTER TABLE ong_horarios_visita RENAME TO ngo_visiting_hours;

ALTER TABLE ngo_visiting_hours RENAME COLUMN usuario_id TO user_id;
ALTER TABLE ngo_visiting_hours RENAME COLUMN ordem      TO sort_order;
ALTER TABLE ngo_visiting_hours RENAME COLUMN dias       TO days;
ALTER TABLE ngo_visiting_hours RENAME COLUMN horario    TO hours;

ALTER TABLE ngo_visiting_hours RENAME CONSTRAINT ong_horarios_visita_pkey            TO ngo_visiting_hours_pkey;
ALTER TABLE ngo_visiting_hours RENAME CONSTRAINT ong_horarios_visita_usuario_id_fkey TO ngo_visiting_hours_user_id_fkey;

-- ------------------------------------------------------------
-- PostgreSQL 18+ dá nome às restrições NOT NULL (tabela_coluna_not_null) e
-- não as renomeia junto com a coluna. Em versões anteriores elas não existem
-- no catálogo e o laço simplesmente não encontra nada.
-- ------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass::text AS table_name, c.conname, a.attname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.contype = 'n'
          AND c.conrelid::regclass::text IN ('users', 'ngo_team', 'ngo_visiting_hours')
          AND c.conname <> c.conrelid::regclass::text || '_' || a.attname || '_not_null'
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                       r.table_name, r.conname, r.table_name || '_' || r.attname || '_not_null');
    END LOOP;
END $$;

COMMIT;
