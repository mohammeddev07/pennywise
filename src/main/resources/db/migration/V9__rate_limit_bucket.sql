-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

-- Token bucket per key (user, or client IP for auth endpoints). A row that has not been
-- touched for longer than a full refill is equivalent to no row, so old rows are swept.
CREATE TABLE rate_limit_bucket
(
    bucket_key TEXT PRIMARY KEY,
    tokens     NUMERIC     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Atomic under concurrency: the row lock from SELECT ... FOR UPDATE serialises callers on the
-- same key, so two requests can never both spend the same tokens.
-- p_now exists so tests can pin the clock; production callers omit it.
CREATE FUNCTION consume_tokens(
    p_key TEXT,
    p_cost NUMERIC,
    p_capacity NUMERIC,
    p_refill_per_sec NUMERIC,
    p_now TIMESTAMPTZ DEFAULT clock_timestamp()
)
RETURNS TABLE (allowed BOOLEAN, retry_after_seconds INTEGER)
LANGUAGE plpgsql
SET search_path = expense_tracker, public
AS $$
DECLARE
    v_cost    NUMERIC := least(p_cost, p_capacity);
    v_tokens  NUMERIC;
    v_updated TIMESTAMPTZ;
BEGIN
    INSERT INTO rate_limit_bucket (bucket_key, tokens, updated_at)
    VALUES (p_key, p_capacity, p_now)
    ON CONFLICT (bucket_key) DO NOTHING;

    SELECT b.tokens, b.updated_at INTO v_tokens, v_updated
    FROM rate_limit_bucket b
    WHERE b.bucket_key = p_key
    FOR UPDATE;

    v_tokens := least(
        p_capacity,
        v_tokens + greatest(0, extract(EPOCH FROM (p_now - v_updated))) * p_refill_per_sec
    );
    v_updated := greatest(p_now, v_updated);

    IF v_tokens >= v_cost THEN
        UPDATE rate_limit_bucket SET tokens = v_tokens - v_cost, updated_at = v_updated
        WHERE bucket_key = p_key;
        RETURN QUERY SELECT TRUE, 0;
    ELSE
        UPDATE rate_limit_bucket SET tokens = v_tokens, updated_at = v_updated
        WHERE bucket_key = p_key;
        RETURN QUERY SELECT
            FALSE,
            CASE WHEN p_refill_per_sec > 0
                THEN ceil((v_cost - v_tokens) / p_refill_per_sec)::INTEGER
                ELSE 3600
            END;
    END IF;
END;
$$;
