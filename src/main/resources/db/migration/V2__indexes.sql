-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
-- Partial unique for category names within a book/type (ignoring soft-deleted)
-- noinspection SqlResolveInspection
CREATE UNIQUE INDEX ux_categories_book_type_name_active
    ON expense_tracker.categories (book_id, type, lower(name)) WHERE deleted_at IS NULL;

-- Books: quick lookup by owner, and active books
-- noinspection SqlResolveInspection
CREATE INDEX ix_books_owner_active ON expense_tracker.books (owner_user_id) WHERE deleted_at IS NULL;

-- Transactions list/filter indexes (active only)
-- noinspection SqlResolveInspection
CREATE INDEX ix_tx_book_occurred_id_active
    ON expense_tracker.transactions (book_id, occurred_on DESC, id DESC) WHERE deleted_at IS NULL;

-- noinspection SqlResolveInspection
CREATE INDEX ix_tx_book_type_active
    ON expense_tracker.transactions (book_id, type) WHERE deleted_at IS NULL;

-- noinspection SqlResolveInspection
CREATE INDEX ix_tx_book_category_active
    ON expense_tracker.transactions (book_id, category_id) WHERE deleted_at IS NULL;

-- noinspection SqlResolveInspection
CREATE INDEX ix_tx_book_updated_active
    ON expense_tracker.transactions (book_id, updated_at DESC) WHERE deleted_at IS NULL;

-- Export jobs
-- noinspection SqlResolveInspection
CREATE INDEX ix_export_jobs_book_active ON expense_tracker.export_jobs (book_id) WHERE deleted_at IS NULL;
-- noinspection SqlResolveInspection
CREATE INDEX ix_export_jobs_user_active ON expense_tracker.export_jobs (requested_by_user_id) WHERE deleted_at IS NULL;

-- Optional note search (enable later)
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- CREATE INDEX ix_tx_note_trgm_active
--   ON expense_tracker.transactions USING gin (note gin_trgm_ops)
--   WHERE deleted_at IS NULL AND note IS NOT NULL;
