-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

-- transactions.created_at is the record's creation time and must never change after insert.
-- The application already maps the column updatable=false; this trigger is the hard guarantee
-- for any writer (psql, migrations, future code). Scoped to transactions only: other tables have
-- no demonstrated need yet. Existing rows are not touched.
CREATE OR REPLACE FUNCTION reject_created_at_change() RETURNS trigger AS
$$
BEGIN
    IF NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'transactions.created_at is immutable (id=%)', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_created_at_immutable
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    WHEN (OLD.created_at IS DISTINCT FROM NEW.created_at)
EXECUTE FUNCTION reject_created_at_change();
