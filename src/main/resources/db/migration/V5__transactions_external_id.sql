-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

ALTER TABLE transactions
    ADD COLUMN external_id TEXT NULL CHECK (external_id IS NULL OR char_length(external_id) <= 200);

CREATE UNIQUE INDEX ux_transactions_book_external_id_active
    ON transactions (book_id, external_id)
    WHERE deleted_at IS NULL AND external_id IS NOT NULL;
