-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

-- Google's stable account id ("sub" claim of a verified ID token). NULL for users who have never
-- used Google. UNIQUE so one Google account can only ever belong to one Pennywise user.
ALTER TABLE users
    ADD COLUMN google_sub TEXT NULL UNIQUE;

-- One active account per email, case-insensitively, enforced here rather than by a check-then-insert
-- in the application (password signup and first Google login can race). Emails from external
-- issuers are stored verbatim, hence lower(). Rows without an email are exempt.
CREATE UNIQUE INDEX uq_users_email_active
    ON users (lower(email))
    WHERE deleted_at IS NULL AND email IS NOT NULL;
