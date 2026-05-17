-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

ALTER TABLE users
    ADD COLUMN default_currency_code VARCHAR(3) NULL;

ALTER TABLE categories
    ADD COLUMN icon TEXT NULL,
    ADD COLUMN color VARCHAR(7) NULL CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$');
