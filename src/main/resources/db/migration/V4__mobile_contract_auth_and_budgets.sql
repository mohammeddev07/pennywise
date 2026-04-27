-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

ALTER TABLE users
    ADD COLUMN password_hash TEXT NULL,
    ADD COLUMN default_currency_code VARCHAR(3) NULL;

ALTER TABLE categories
    ADD COLUMN icon TEXT NULL,
    ADD COLUMN color VARCHAR(7) NULL CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$');

ALTER TABLE transactions
    ADD COLUMN title TEXT NULL CHECK (title IS NULL OR char_length(title) <= 120),
    ADD COLUMN payment_method TEXT NULL CHECK (payment_method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'WALLET', 'OTHER')),
    ADD COLUMN occurred_at TIMESTAMPTZ NULL;

UPDATE transactions
SET occurred_at = occurred_on::timestamp AT TIME ZONE 'UTC'
WHERE occurred_at IS NULL;

CREATE TABLE budgets
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    book_id      UUID        NOT NULL REFERENCES books (id),
    category_id  UUID        NOT NULL REFERENCES categories (id),
    month_start  DATE        NOT NULL,
    amount_minor BIGINT      NOT NULL CHECK (amount_minor >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ NULL,
    version      BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_budgets_book_category_month_active
    ON budgets (book_id, category_id, month_start)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_budgets_book_month
    ON budgets (book_id, month_start)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_book_occurred_at
    ON transactions (book_id, occurred_at DESC)
    WHERE deleted_at IS NULL;
