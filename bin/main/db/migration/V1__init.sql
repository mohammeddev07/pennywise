-- noinspection SqlNoDataSourceInspection, SqlResolveInspection
-- language=PostgreSQL
-- Extensions (Enable functions like gen_random_uuid())
CREATE
    EXTENSION IF NOT EXISTS pgcrypto;

-- Ensure your app schema exists (since you’re using expense_tracker)
CREATE
    SCHEMA IF NOT EXISTS expense_tracker;

-- Make sure subsequent statements target the schema
SET search_path = expense_tracker, public;

-- USERS
CREATE TABLE users
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    auth_subject TEXT        NOT NULL UNIQUE,
    email        TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ NULL,
    version      BIGINT      NOT NULL DEFAULT 0
);

-- BOOKS
CREATE TABLE books
(
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    owner_user_id         UUID        NOT NULL REFERENCES users (id),
    name                  TEXT        NOT NULL,
    currency_code         VARCHAR(3)  NOT NULL,
    timezone              TEXT        NOT NULL,
    opening_balance_minor BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ NULL,
    version               BIGINT      NOT NULL DEFAULT 0
);

-- CATEGORIES
CREATE TABLE categories
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    book_id     UUID        NOT NULL REFERENCES books (id),
    type        TEXT        NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    name        TEXT        NOT NULL,
    is_disabled BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ NULL,
    version     BIGINT      NOT NULL DEFAULT 0
);

-- TRANSACTIONS
CREATE TABLE transactions
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    book_id      UUID        NOT NULL REFERENCES books (id),
    type         TEXT        NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
    occurred_on  DATE        NOT NULL,
    category_id  UUID        NOT NULL REFERENCES categories (id),
    note         TEXT        NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ NULL,
    version      BIGINT      NOT NULL DEFAULT 0
);

-- EXPORT JOBS (Phase 1: minimal scaffold)
CREATE TABLE export_jobs
(
    id                   UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    book_id              UUID        NOT NULL REFERENCES books (id),
    requested_by_user_id UUID        NOT NULL REFERENCES users (id),
    status               TEXT        NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    file_name            TEXT        NULL,
    storage_key          TEXT        NULL, -- later: S3 key
    error_message        TEXT        NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);

-- IDEMPOTENCY KEYS (per-user)
CREATE TABLE idempotency_keys
(
    user_id       UUID        NOT NULL REFERENCES users (id),
    idem_key      TEXT        NOT NULL,
    request_hash  TEXT        NOT NULL,
    response_code INT         NULL,
    response_body TEXT        NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, idem_key)
);
