-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

-- Name shown in the app's greeting. NULL until the user sets one.
ALTER TABLE users
    ADD COLUMN display_name VARCHAR(40) NULL;
