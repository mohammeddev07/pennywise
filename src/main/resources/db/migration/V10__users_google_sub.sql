-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

-- Google's stable account id ("sub" claim of a verified ID token). NULL for users who have never
-- used Google. UNIQUE so one Google account can only ever belong to one Pennywise user.
ALTER TABLE users
    ADD COLUMN google_sub TEXT NULL UNIQUE;
