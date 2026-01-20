-- noinspection SqlNoDataSourceInspection
-- language=PostgreSQL
-- Ensure your app schema is targeted
SET search_path = expense_tracker, public;

-- Partial unique for category names within a book/type (ignoring soft-deleted)
CREATE UNIQUE INDEX ux_categories_book_type_name_active
    ON categories (book_id, type, lower(name)) WHERE deleted_at IS NULL;

-- Books: quick lookup by owner, and active books
CREATE INDEX ix_books_owner_active ON books (owner_user_id) WHERE deleted_at IS NULL;

-- Transactions list/filter indexes (active only)
CREATE INDEX ix_tx_book_occurred_id_active
    ON transactions (book_id, occurred_on DESC, id DESC) WHERE deleted_at IS NULL;

CREATE INDEX ix_tx_book_type_active
    ON transactions (book_id, type) WHERE deleted_at IS NULL;

CREATE INDEX ix_tx_book_category_active
    ON transactions (book_id, category_id) WHERE deleted_at IS NULL;

CREATE INDEX ix_tx_book_updated_active
    ON transactions (book_id, updated_at DESC) WHERE deleted_at IS NULL;

-- Export jobs
CREATE INDEX ix_export_jobs_book_active ON export_jobs (book_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_export_jobs_user_active ON export_jobs (requested_by_user_id) WHERE deleted_at IS NULL;

-- Optional note search (enable later)
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- CREATE INDEX ix_tx_note_trgm_active
--   ON transactions USING gin (note gin_trgm_ops)
--   WHERE deleted_at IS NULL AND note IS NOT NULL;
