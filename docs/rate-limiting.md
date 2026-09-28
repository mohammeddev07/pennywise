# Rate limiting

Token buckets guard every endpoint except health and API documentation. A request spends tokens
in proportion to the work it causes, and a bucket refills continuously, so there is no window
boundary to burst across.

| Bucket | Key                      | Store                       | Capacity | Refill |
| ------ | ------------------------ | --------------------------- | -------- | ------ |
| User   | JWT subject              | Postgres (`consume_tokens`) | 60       | 2/s    |
| IP     | client IP, `/v1/auth/**` | Postgres (`consume_tokens`) | 8        | 0.1/s  |
| Global | whole backend            | In memory (single instance) | 200      | 50/s   |

The caller's own bucket is checked first, so a flooding caller cannot drain the global bucket with
rejected requests. Global numbers are an estimate for Render's free tier with a three-connection
database pool and have not been load-tested.

## Costs

Read 1, list or aggregate 2, write 2, search 3, range summary or analysis 5, export 25, import 40,
AI filter 20, login or signup 1. Costs above a bucket's capacity are capped at its capacity.

All numbers live in `RateLimitProperties` and can be overridden with `app.rate-limit.*`.
`RATE_LIMIT_ENABLED=false` switches the feature off.

## Behavior

- Rejection is `429` with a `Retry-After` header and error code `RATE_LIMITED`.
- If the database check itself fails, the request is allowed and a warning is logged.
- Idle bucket rows are swept hourly by `PostgresTokenBucket.sweepIdle`.
- There is deliberately no daily cap: this is a personal expense tracker with no metered cost.
- `server.forward-headers-strategy=native` makes the client IP come from Render's proxy header.
  After deploying, confirm two different devices do not share one `auth-ip:` bucket row.
