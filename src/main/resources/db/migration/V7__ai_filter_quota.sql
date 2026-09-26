-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
SET search_path = expense_tracker, public;

-- One row per (user, day); count is reserved atomically by AiFilterQuotaRepository
-- before each Gemini call so concurrent requests from the same user can't exceed
-- the daily cap even across multiple app instances.
CREATE TABLE ai_filter_quota
(
    user_id UUID NOT NULL,
    day     DATE NOT NULL,
    count   INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);
