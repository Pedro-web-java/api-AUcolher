-- ============================================================
-- Migração 002 — animais e favoritos com nomes em inglês
--
-- Rode depois da 001. Renomeia tabelas, colunas e constraints e troca
-- os valores dos enums (CACHORRO -> DOG, DISPONIVEL -> AVAILABLE...)
-- sem apagar nenhum dado. Banco novo não precisa disto — o schema.sql
-- atual já cria tudo em inglês.
--
--   psql -U postgres -h localhost -d aucolher_db -f sql/migrations/002_animals_english.sql
--
-- Roda numa transação só: se algo falhar, nada muda.
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- animais -> animals
-- ------------------------------------------------------------
ALTER TABLE animais RENAME TO animals;

ALTER TABLE animals RENAME COLUMN dono_id                TO owner_id;
ALTER TABLE animals RENAME COLUMN nome                   TO name;
ALTER TABLE animals RENAME COLUMN especie                TO species;
ALTER TABLE animals RENAME COLUMN raca                   TO breed;
ALTER TABLE animals RENAME COLUMN sexo                   TO sex;
ALTER TABLE animals RENAME COLUMN idade_valor            TO age_value;
ALTER TABLE animals RENAME COLUMN idade_unidade          TO age_unit;
ALTER TABLE animals RENAME COLUMN faixa_etaria           TO age_group;
ALTER TABLE animals RENAME COLUMN porte                  TO size;
ALTER TABLE animals RENAME COLUMN vacinado               TO vaccinated;
ALTER TABLE animals RENAME COLUMN castrado               TO neutered;
ALTER TABLE animals RENAME COLUMN vermifugado            TO dewormed;
ALTER TABLE animals RENAME COLUMN necessidades_especiais TO special_needs;
ALTER TABLE animals RENAME COLUMN nivel_energia          TO energy_level;
ALTER TABLE animals RENAME COLUMN temperamento           TO temperament;
ALTER TABLE animals RENAME COLUMN nivel_independencia    TO independence_level;
ALTER TABLE animals RENAME COLUMN nivel_vocalizacao      TO vocalization;
ALTER TABLE animals RENAME COLUMN bom_com_criancas       TO good_with_children;
ALTER TABLE animals RENAME COLUMN bom_com_caes           TO good_with_dogs;
ALTER TABLE animals RENAME COLUMN bom_com_gatos          TO good_with_cats;
ALTER TABLE animals RENAME COLUMN adaptado_apartamento   TO apartment_friendly;
ALTER TABLE animals RENAME COLUMN resumo                 TO summary;
ALTER TABLE animals RENAME COLUMN historia               TO story;
ALTER TABLE animals RENAME COLUMN data_criacao           TO created_at;
ALTER TABLE animals RENAME COLUMN data_atualizacao       TO updated_at;

ALTER SEQUENCE animais_id_seq RENAME TO animals_id_seq;
ALTER INDEX idx_animais_status_data RENAME TO idx_animals_status_created;
ALTER INDEX idx_animais_dono        RENAME TO idx_animals_owner;

ALTER TABLE animals RENAME CONSTRAINT animais_pkey         TO animals_pkey;
ALTER TABLE animals RENAME CONSTRAINT animais_dono_id_fkey TO animals_owner_id_fkey;

-- Os CHECKs citam os valores antigos: saem antes de trocar os valores e voltam com os novos
ALTER TABLE animals DROP CONSTRAINT chk_animais_especie;
ALTER TABLE animals DROP CONSTRAINT chk_animais_sexo;
ALTER TABLE animals DROP CONSTRAINT chk_animais_porte;
ALTER TABLE animals DROP CONSTRAINT chk_animais_idade_unidade;
ALTER TABLE animals DROP CONSTRAINT chk_animais_idade;
ALTER TABLE animals DROP CONSTRAINT chk_animais_faixa_etaria;
ALTER TABLE animals DROP CONSTRAINT chk_animais_niveis;
ALTER TABLE animals DROP CONSTRAINT chk_animais_temperamento;
ALTER TABLE animals DROP CONSTRAINT chk_animais_status;

UPDATE animals SET
    species = CASE species WHEN 'CACHORRO' THEN 'DOG' WHEN 'GATO' THEN 'CAT' WHEN 'OUTRO' THEN 'OTHER' ELSE species END,
    sex = CASE sex WHEN 'MACHO' THEN 'MALE' WHEN 'FEMEA' THEN 'FEMALE' ELSE sex END,
    size = CASE size WHEN 'PEQUENO' THEN 'SMALL' WHEN 'MEDIO' THEN 'MEDIUM' WHEN 'GRANDE' THEN 'LARGE' ELSE size END,
    age_unit = CASE age_unit WHEN 'ANOS' THEN 'YEARS' WHEN 'MESES' THEN 'MONTHS' ELSE age_unit END,
    age_group = CASE age_group WHEN 'FILHOTE' THEN 'PUPPY' WHEN 'ADULTO' THEN 'ADULT' WHEN 'IDOSO' THEN 'SENIOR' ELSE age_group END,
    energy_level = CASE energy_level WHEN 'BAIXO' THEN 'LOW' WHEN 'MODERADO' THEN 'MODERATE' WHEN 'ALTO' THEN 'HIGH' ELSE energy_level END,
    independence_level = CASE independence_level WHEN 'BAIXO' THEN 'LOW' WHEN 'MODERADO' THEN 'MODERATE' WHEN 'ALTO' THEN 'HIGH' ELSE independence_level END,
    vocalization = CASE vocalization WHEN 'BAIXO' THEN 'LOW' WHEN 'MODERADO' THEN 'MODERATE' WHEN 'ALTO' THEN 'HIGH' ELSE vocalization END,
    temperament = CASE temperament
        WHEN 'CALMO' THEN 'CALM' WHEN 'BRINCALHAO' THEN 'PLAYFUL' WHEN 'AFETUOSO' THEN 'AFFECTIONATE'
        WHEN 'PROTETOR' THEN 'PROTECTIVE' WHEN 'INDEPENDENTE' THEN 'INDEPENDENT' ELSE temperament END,
    status = CASE status WHEN 'DISPONIVEL' THEN 'AVAILABLE' WHEN 'ADOTADO' THEN 'ADOPTED' WHEN 'INATIVO' THEN 'INACTIVE' ELSE status END;

ALTER TABLE animals ALTER COLUMN status SET DEFAULT 'AVAILABLE';

ALTER TABLE animals ADD CONSTRAINT chk_animals_species     CHECK (species IN ('DOG', 'CAT', 'OTHER'));
ALTER TABLE animals ADD CONSTRAINT chk_animals_sex         CHECK (sex IN ('MALE', 'FEMALE'));
ALTER TABLE animals ADD CONSTRAINT chk_animals_size        CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE'));
ALTER TABLE animals ADD CONSTRAINT chk_animals_age_unit    CHECK (age_unit IN ('YEARS', 'MONTHS'));
ALTER TABLE animals ADD CONSTRAINT chk_animals_age         CHECK ((age_unit = 'MONTHS' AND age_value BETWEEN 0 AND 11)
                                                               OR (age_unit = 'YEARS'  AND age_value BETWEEN 1 AND 30));
ALTER TABLE animals ADD CONSTRAINT chk_animals_age_group   CHECK (age_group IN ('PUPPY', 'ADULT', 'SENIOR'));
ALTER TABLE animals ADD CONSTRAINT chk_animals_levels      CHECK (energy_level       IN ('LOW', 'MODERATE', 'HIGH')
                                                              AND independence_level IN ('LOW', 'MODERATE', 'HIGH')
                                                              AND vocalization       IN ('LOW', 'MODERATE', 'HIGH'));
ALTER TABLE animals ADD CONSTRAINT chk_animals_temperament CHECK (temperament IN ('CALM', 'PLAYFUL', 'AFFECTIONATE', 'PROTECTIVE', 'INDEPENDENT'));
ALTER TABLE animals ADD CONSTRAINT chk_animals_status      CHECK (status IN ('AVAILABLE', 'ADOPTED', 'INACTIVE'));

COMMENT ON COLUMN animals.age_value IS 'Idade informada no cadastro, na unidade de age_unit (0-11 meses ou 1-30 anos)';
COMMENT ON COLUMN animals.age_group IS 'Calculada pela API ao salvar: < 12 meses PUPPY, < 8 anos ADULT, senão SENIOR';
COMMENT ON COLUMN animals.status    IS 'AVAILABLE, ADOPTED ou INACTIVE (retirado do ar pelo dono — exclusão lógica)';

-- ------------------------------------------------------------
-- animal_fotos -> animal_photos
-- ------------------------------------------------------------
ALTER TABLE animal_fotos RENAME TO animal_photos;

ALTER TABLE animal_photos RENAME COLUMN ordem TO sort_order;

ALTER TABLE animal_photos RENAME CONSTRAINT animal_fotos_pkey           TO animal_photos_pkey;
ALTER TABLE animal_photos RENAME CONSTRAINT animal_fotos_animal_id_fkey TO animal_photos_animal_id_fkey;

COMMENT ON TABLE animal_photos IS 'Fotos do anúncio; sort_order 0 é a capa usada no card da listagem';

-- ------------------------------------------------------------
-- favoritos -> favorites
-- ------------------------------------------------------------
ALTER TABLE favoritos RENAME TO favorites;

ALTER TABLE favorites RENAME COLUMN usuario_id   TO user_id;
ALTER TABLE favorites RENAME COLUMN data_criacao TO created_at;

ALTER SEQUENCE favoritos_id_seq RENAME TO favorites_id_seq;
ALTER INDEX idx_favoritos_usuario RENAME TO idx_favorites_user;

ALTER TABLE favorites RENAME CONSTRAINT favoritos_pkey              TO favorites_pkey;
ALTER TABLE favorites RENAME CONSTRAINT favoritos_usuario_id_fkey   TO favorites_user_id_fkey;
ALTER TABLE favorites RENAME CONSTRAINT favoritos_animal_id_fkey    TO favorites_animal_id_fkey;
ALTER TABLE favorites RENAME CONSTRAINT uq_favoritos_usuario_animal TO uq_favorites_user_animal;

-- ------------------------------------------------------------
-- PostgreSQL 18+: restrições NOT NULL nomeadas (ver migração 001)
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
          AND c.conrelid::regclass::text IN ('animals', 'animal_photos', 'favorites')
          AND c.conname <> c.conrelid::regclass::text || '_' || a.attname || '_not_null'
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                       r.table_name, r.conname, r.table_name || '_' || r.attname || '_not_null');
    END LOOP;
END $$;

COMMIT;
