-- PostgreSQL: conserver les tickets existants lors de l'ajout du suivi des champs IA.
ALTER TABLE ticket
    ADD COLUMN IF NOT EXISTS analysis_fields_tracked boolean NOT NULL DEFAULT false;
