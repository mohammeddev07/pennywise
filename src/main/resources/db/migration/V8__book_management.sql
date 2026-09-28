-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;
SET LOCAL lock_timeout = '5s';

ALTER TABLE books
    ADD COLUMN icon TEXT NOT NULL DEFAULT 'book',
    ADD COLUMN color TEXT NOT NULL DEFAULT 'green',
    ADD COLUMN sort_order BIGINT NOT NULL DEFAULT 0;

-- Preserve audit/financial columns. Rank every existing book, including archived rows.
WITH ranked AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY owner_user_id
            ORDER BY created_at, id
        ) - 1 AS book_position
    FROM books
)

UPDATE books AS b
SET sort_order = ranked.book_position
FROM ranked
WHERE b.id = ranked.id;
