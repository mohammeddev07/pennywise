-- Ensure schema
SET search_path = expense_tracker, public;

DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'expense_tracker'
              AND table_name = 'transactions'
              AND column_name = 'note'
              AND data_type = 'bytea'
        ) THEN
            ALTER TABLE expense_tracker.transactions
                ALTER COLUMN note TYPE text
                    USING convert_from(note, 'UTF8');
        END IF;
    END $$;
