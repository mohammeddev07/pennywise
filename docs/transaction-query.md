# Transaction query API (P1.2 search, P1.3 analyze + export)

Three POST endpoints share one filter language and one predicate compiler, so **search, analytics and
Excel export always see exactly the same rows**. The old GET routes (`GET …/transactions`,
`GET …/transactions/export`, `GET …/summary/*`) are unchanged.

| Endpoint | Purpose |
| --- | --- |
| `POST /v1/books/{bookId}/transactions/search` | filtered, sorted, offset-paged list |
| `POST /v1/books/{bookId}/transactions/analyze` | totals, category/bucket aggregates, largest expense |
| `POST /v1/books/{bookId}/transactions/export/query` | `.xlsx` of **all** matching rows |

Base path is `/api` in every deployment (`server.servlet.context-path`). Auth is the normal bearer JWT.
Shared TypeScript types: [`transaction-query.types.ts`](transaction-query.types.ts).

## Scope and safety

The executed predicate is always

```
ownedBook AND activeTransaction AND categoryInBook AND (userExpression)
```

* `bookId` in the path is the only book scope. An unknown book or a book owned by someone else is
  `404 NOT_FOUND` before the body is looked at. There is no `bookId` field.
* `deletedAt` is always `null`: soft-deleted rows never match and cannot be requested.
* `version` is an optimistic-locking token for `If-Match`, not user data, so it is not filterable.
* The user expression is one parenthesised operand; an `OR` group cannot widen the tenant scope.
* Category ids are resolved inside the owned book. A category id from another book simply matches
  nothing (no error, nothing leaked). Categories that are disabled or soft-deleted still resolve for
  the active transactions that reference them (historical categories), so old rows never lose their
  category name or drop out of a `categoryName`/`categoryId` filter.
* Field names come from a fixed enum and every value is a bound SQL parameter; nothing from the
  request is concatenated into SQL.

## Filter AST

```jsonc
group     = { "kind": "group", "op": "AND" | "OR", "children": [ node, ... ] }
condition = { "kind": "condition", "field": "...", "operator": "...", "value": ... }   // value omitted for IS_NULL / IS_NOT_NULL
node      = group | condition
```

* `filter` may be a group or a single condition. **An empty top-level `AND`** (or an omitted
  `filter`) means "all active rows of this book". Empty nested groups and an empty `OR` are `400`.
* Limits: nesting depth ≤ 3 (the top-level group is level 1), ≤ 30 conditions in total, ≤ 100 values
  per `IN`/`NOT_IN`, ≤ 3 user sort keys, ≤ 280 characters per text value (counted in code points),
  request body ≤ 64 KiB (`413 REQUEST_TOO_LARGE`). Unknown properties, duplicate JSON keys and wrong
  value types are rejected before any SQL runs.
* Value formats: UUID = canonical 36-char string; enum = exact upper-case name (`"CARD"`);
  `amountMinor` = JSON integer `0 … 999999999999999`; date = `"YYYY-MM-DD"`; timestamp = ISO-8601
  **with offset** (`"2026-01-31T10:15:00Z"`, normalised to UTC, truncated to microseconds); text =
  non-empty string with no NUL character (use `IS_NULL` to look for missing text).
* `IN`/`NOT_IN` take a non-empty array. `BETWEEN` takes `[lower, upper]`, is **inclusive**, and a
  reversed range is `400`.

### Capability matrix (field × operators)

`Nullable` = accepts `IS_NULL`/`IS_NOT_NULL`. Every field listed here can also be sorted.

| Field | Type | Nullable | Operators |
| --- | --- | --- | --- |
| `id` | UUID | no | EQ, IN, NE, NOT_IN |
| `type` | ENUM | no | EQ, IN, NE, NOT_IN |
| `amountMinor` | NUMBER | no | BETWEEN, EQ, GT, GTE, LT, LTE, NE |
| `occurredOn` | DATE | no | BETWEEN, EQ, GT, GTE, LT, LTE, NE |
| `occurredAt` | TIMESTAMP | yes | BETWEEN, EQ, GT, GTE, IS_NOT_NULL, IS_NULL, LT, LTE, NE |
| `categoryId` | UUID | no | EQ, IN, NE, NOT_IN |
| `categoryName` | TEXT | no | CONTAINS, ENDS_WITH, EQ, NE, NOT_CONTAINS, STARTS_WITH |
| `paymentMethod` | ENUM | yes | EQ, IN, IS_NOT_NULL, IS_NULL, NE, NOT_IN |
| `title` | TEXT | yes | CONTAINS, ENDS_WITH, EQ, IS_NOT_NULL, IS_NULL, NE, NOT_CONTAINS, STARTS_WITH |
| `note` | TEXT | yes | CONTAINS, ENDS_WITH, EQ, IS_NOT_NULL, IS_NULL, NE, NOT_CONTAINS, STARTS_WITH |
| `description` | TEXT | no | CONTAINS, ENDS_WITH, EQ, NE, NOT_CONTAINS, STARTS_WITH |
| `createdAt` | TIMESTAMP | no | BETWEEN, EQ, GT, GTE, LT, LTE, NE |
| `updatedAt` | TIMESTAMP | no | BETWEEN, EQ, GT, GTE, LT, LTE, NE |
| `externalId` | TEXT | yes | CONTAINS, ENDS_WITH, EQ, IS_NOT_NULL, IS_NULL, NE, NOT_CONTAINS, STARTS_WITH |

Enum values: `type` = `INCOME | EXPENSE`; `paymentMethod` = `CASH | CARD | BANK_TRANSFER | WALLET | OTHER`.
`QueryRequestParserTest` fails when this table and the code drift.

### Semantics you must know

* **Text matching is literal and case-insensitive** (`lower(col)` against `lower(value)`).
  `%`, `_` and `\` in a value are ordinary characters, never wildcards. `EQ` is a whole-value match.
* **Nulls.** A comparison against a non-null value never matches a null column, **including `NE`,
  `NOT_IN` and `NOT_CONTAINS`** (`paymentMethod NE "CARD"` does not return rows without a payment
  method). To include them, OR with `IS_NULL`:
  `OR( paymentMethod NE CARD , paymentMethod IS_NULL )`.
* **`description`** is virtual: `title` OR `note`. Positive operators (`EQ`, `CONTAINS`,
  `STARTS_WITH`, `ENDS_WITH`) match when either field matches. Negative operators (`NE`,
  `NOT_CONTAINS`) are the *logical negation* over the values coalesced to `''`, so a row with no
  title and no note **does** match `description NOT_CONTAINS "x"` (unlike `title NOT_CONTAINS "x"`,
  which skips null titles).
* `categoryName` is the category's current name (joined once, in the same statement).

## Sorting

`sort: [{ "field": "...", "direction": "ASC" | "DESC" }]`, at most 3 keys, no repeated field.

* Default (no `sort`): `occurredOn DESC, createdAt DESC, id DESC`.
* If `id` is not among the keys it is appended as `id ASC`, so the order is total and stable.
* Text fields sort on `lower(value)` (case-normalised); the collation is the database's. `type` and
  `paymentMethod` sort by their stored name, not by a display order.
* Nullable fields use explicit `NULLS LAST` in **both** directions. Non-nullable columns have no
  nulls to place, so no clause is emitted (this lets the default sort use the existing index).
* Virtual `description` sorts by `lower(coalesce(title,''))`, then `lower(coalesce(note,''))`; rows
  with neither therefore come first in `ASC`.
* Clients must **not** re-sort a page.

## Search

```http
POST /api/v1/books/7d1c…/transactions/search
Content-Type: application/json
```

```json
{
  "filter": {
    "kind": "group", "op": "AND",
    "children": [
      { "kind": "condition", "field": "type", "operator": "EQ", "value": "EXPENSE" },
      { "kind": "group", "op": "OR", "children": [
        { "kind": "condition", "field": "description", "operator": "CONTAINS", "value": "coffee" },
        { "kind": "condition", "field": "amountMinor", "operator": "GTE", "value": 10000 }
      ]},
      { "kind": "condition", "field": "occurredOn", "operator": "BETWEEN", "value": ["2026-03-01", "2026-03-31"] }
    ]
  },
  "sort": [
    { "field": "amountMinor", "direction": "DESC" },
    { "field": "occurredOn", "direction": "ASC" }
  ],
  "page": { "offset": 0, "limit": 50 }
}
```

```json
{
  "items": [
    {
      "id": "0b1f…", "bookId": "7d1c…", "type": "EXPENSE", "amountMinor": 12000,
      "occurredOn": "2026-03-03", "occurredAt": "2026-03-03T12:00:00Z", "title": "Rent",
      "categoryId": "5a2e…", "category": { "id": "5a2e…", "name": "Rent", "type": "EXPENSE" },
      "paymentMethod": null, "note": "monthly rent", "externalId": null,
      "createdAt": "2026-03-03T12:01:10.123456Z", "updatedAt": "2026-03-03T12:01:10.123456Z",
      "deletedAt": null, "version": 0
    }
  ],
  "totalCount": 137,
  "page": { "offset": 0, "limit": 50, "hasMore": true },
  "queryFingerprint": "9c1a…64 hex chars…"
}
```

* `items` are the same objects as `GET …/transactions/{id}`.
* `page.limit`: default 50, `1…200` (out of range is `400`, not clamped).
* `totalCount` counts **all** matching rows; `hasMore = offset + items.length < totalCount`.
* `queryFingerprint` is a SHA-256 of the normalised filter + applied sort (not the page). It changes
  when the query changes.

### Pagination contract

Paging is **bounded offset pagination for every sort**, `page.offset` ≤ **100 000**. A larger offset is
`400 PAGE_OFFSET_LIMIT_EXCEEDED` ("narrow the filter or change the sort") — the server never silently
truncates. `totalCount` and the items of *one* response come from one database snapshot, but
**consecutive pages are independent snapshots, not a cross-request snapshot**. Therefore clients must
reset to `offset: 0`:

* after any create / edit / delete / import that they (or the user) performed,
* on pull-to-refresh or when the screen regains focus,
* whenever `queryFingerprint` changes (different filter or sort).

Otherwise a row can appear twice or be skipped while data changes between page requests. The legacy
`GET …/transactions` cursor contract is unchanged.

## Analyze

```http
POST /api/v1/books/{bookId}/transactions/analyze
```

```json
{
  "filter": { "kind": "group", "op": "AND", "children": [
    { "kind": "condition", "field": "paymentMethod", "operator": "IS_NULL" }
  ]},
  "bucket": "MONTH",
  "window": { "startDate": "2025-03-15", "endDate": "2025-04-30" }
}
```

* `bucket`: `DAY | MONTH | YEAR`. `window` is an explicit **inclusive ledger-date window**
  (`occurredOn`), required, `startDate ≤ endDate`, at most 5 years (same guard as
  `GET /summary/range`). The caller resolves defaults; the effective window is echoed back.
* The final predicate is `filter AND occurredOn BETWEEN [startDate, endDate]`. The response returns it
  as `effectiveFilter` (if `filter` is an `AND` group the window condition is appended to it, otherwise
  `filter` and the window are wrapped in a new `AND`). To make **search** return the same rows, send
  `effectiveFilter` (or the same window condition) as its filter. The window counts towards the
  30-condition / depth-3 limits, so analyze accepts only what search would accept.
* Every figure is computed by PostgreSQL over **all** matching rows (one `GROUP BY day, category, type`
  plus one "largest expense" query) inside a single repeatable-read snapshot.
* Not cached: arbitrary filters are high-cardinality. The monthly/balance/budget caches and their
  post-commit eviction are untouched.

```json
{
  "bookId": "7d1c…", "currencyCode": "USD", "bucket": "MONTH",
  "window": { "startDate": "2025-03-15", "endDate": "2025-04-30" },
  "effectiveFilter": { "kind": "group", "op": "AND", "children": [
    { "kind": "condition", "field": "paymentMethod", "operator": "IS_NULL" },
    { "kind": "condition", "field": "occurredOn", "operator": "BETWEEN", "value": ["2025-03-15", "2025-04-30"] }
  ]},
  "queryFingerprint": "…",
  "matchedCount": 3,
  "incomeTotalMinor": 0,
  "expenseTotalMinor": 129000,
  "netMinor": -129000,
  "categories": [
    { "categoryId": "5a2e…", "categoryName": "Rent", "type": "EXPENSE", "totalMinor": 120000, "count": 1, "percentOfExpense": 93.02 },
    { "categoryId": "8c40…", "categoryName": "Groceries", "type": "EXPENSE", "totalMinor": 9000, "count": 2, "percentOfExpense": 6.98 }
  ],
  "buckets": [
    { "key": "2025-03", "start": "2025-03-15", "end": "2025-03-31", "partial": true,
      "incomeTotalMinor": 0, "expenseTotalMinor": 4500, "netMinor": -4500, "count": 1 },
    { "key": "2025-04", "start": "2025-04-01", "end": "2025-04-30", "partial": false,
      "incomeTotalMinor": 0, "expenseTotalMinor": 124500, "netMinor": -124500, "count": 2 }
  ],
  "categoryBuckets": [
    { "categoryId": "5a2e…", "type": "EXPENSE", "bucketKey": "2025-03", "totalMinor": 0, "count": 0 },
    { "categoryId": "5a2e…", "type": "EXPENSE", "bucketKey": "2025-04", "totalMinor": 120000, "count": 1 }
  ],
  "largestExpense": { "id": "0b1f…", "amountMinor": 120000, "…": "same shape as a search item" },
  "monthlyBudgets": null
}
```

Rules:

* Amounts are **positive minor units**, split by `INCOME`/`EXPENSE`; `netMinor = income − expense`.
  Opening balance is **not** part of any figure (it is not "filtered net").
* `categories` are per `(categoryId, type)` and include historical (disabled/deleted) categories of
  matching rows. Order: expenses first, then by total descending, name, id — stable.
* `percentOfExpense` = category total ÷ **filtered** expense total × 100, two decimals, expense
  categories only. It is `null` for income categories and whenever the denominator is 0.
* `buckets` covers **every** period of the window, zeros included. `key` is `YYYY-MM-DD`, `YYYY-MM`
  or `YYYY`. `start`/`end` are clipped to the window and `partial: true` marks an edge period that is
  not a whole calendar month/year inside the window (label it in the UI). Monthly buckets of a year
  sum to that year's yearly bucket for the same window.
* `categoryBuckets` is dense: one cell per `(matching category, bucket)`, zero cells included. If
  `categories × buckets > 20 000` the request fails with `422 ANALYSIS_TOO_LARGE` and a "narrow it"
  message instead of returning an incomplete matrix (e.g. use `MONTH` instead of `DAY`, or shorten the
  window).
* `largestExpense` is the biggest matching `EXPENSE` (ties: latest `occurredOn`, then latest
  `createdAt`, then `id`), or `null`.
* `monthlyBudgets` is non-null **only when the window is exactly one full calendar month**. It lists
  the book's budget targets for that month with a `label` stating they are full-month targets and not
  adjusted for the filter. Never present them as an allowance for a filtered subset.
* Totals that would exceed `9 007 199 254 740 991` (JavaScript's safe integer) return
  `422 AMOUNT_TOTAL_UNSUPPORTED` rather than an inexact number.

## Export

```http
POST /api/v1/books/{bookId}/transactions/export/query
{ "filter": { … }, "sort": [ … ] }
```

Same `filter` and `sort` as search (no `page`; missing `sort` uses the default sort). Returns the
existing `.xlsx` layout (same writer as `GET …/export`): **every** matching row, not the visible page,
in the requested stable order, one `Content-Disposition: attachment` download. All cells are literal
values (a title such as `=1+1` is a text cell, never a formula).

* Runs in one repeatable-read snapshot, reads 1 000 rows at a time and writes through POI's streaming
  workbook, so memory is bounded; the file is built before the response starts, so failures are proper
  JSON errors.
* Limits: **50 000 rows** and **20 MB**. Above that: `422 EXPORT_TOO_LARGE` — "narrow the filter (for
  example a shorter date range)".
* No job system: it is a synchronous download over the existing authenticated request flow.
* `ExternalId` column falls back to the transaction id when there is no external id, so exported
  ids can be reconciled with search results.

## Errors

The usual envelope `{ "error": { "code", "message", "details": [{ "path", "message" }], "requestId" } }`.

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | any shape/type/limit problem; `details[0].path` is the JSON path (`filter.children[2].value[0]`) |
| 400 | `BAD_REQUEST` | body is not parseable JSON (or has duplicate keys) |
| 400 | `PAGE_OFFSET_LIMIT_EXCEEDED` | `page.offset` > 100 000 |
| 404 | `NOT_FOUND` | unknown book or not the caller's book |
| 413 | `REQUEST_TOO_LARGE` | body > 64 KiB |
| 422 | `ANALYSIS_TOO_LARGE` | category × bucket cells > 20 000 |
| 422 | `AMOUNT_TOTAL_UNSUPPORTED` | a total exceeds the JS safe-integer range |
| 422 | `EXPORT_TOO_LARGE` | > 50 000 rows or > 20 MB |

## Frontend integration contract (for P1.4/P1.5)

1. Keep one `TransactionQuery { filter, sort }` object as the source of truth; derive the list, the
   summary and the export from it.
2. List: call `search`, render `items` in the order received, use `queryFingerprint` to detect a
   changed query, and reset to offset 0 after mutations / refresh (see *Pagination contract*).
3. Summary/insights: call `analyze` with the same `filter`, plus `bucket` and the visible window.
   Search for a chart drill-down by appending the category (or bucket range) condition to the same
   filter and the same window; `count`/`totalMinor` of that category equal the search result.
4. Export current results: `POST export/query` with the exact applied `filter` + `sort`; save the
   binary body through the existing authenticated download flow.
5. Label partial buckets, show budgets only when `monthlyBudgets` is present, hide percentages when
   `percentOfExpense` is `null`, and surface `422`/`400` messages verbatim (they say how to narrow).

## Performance notes

EXPLAIN (ANALYZE) on synthetic data — one book with 200 000 active rows plus 300 other tenants with
1 000 rows each (PostgreSQL 16, cold-ish cache, 3 workers):

| Query | Plan | Time |
| --- | --- | --- |
| default sort, first page (`occurredOn/createdAt/id DESC`) | index scan `ix_tx_book_occurred_id_active` + incremental sort | 0.2 ms |
| same with an explicit `NULLS LAST` on the NOT NULL columns | bitmap scan of the whole book + top-N sort | 45 ms |
| filter `type`+`amountMinor`, sort `amountMinor DESC` | book scan + top-N sort | 19 ms |
| `description CONTAINS` count | book scan, `lower(...) LIKE` filter | 29 ms |
| `categoryId` + date range count | `ix_tx_book_category_active` | 3.7 ms |
| analyze `GROUP BY day, category, type`, 5-year window | book scan + hash aggregate | 30 ms |
| sort `title ASC` | book scan + top-N sort | 53 ms |

The second row is why sorts omit `NULLS LAST` on NOT NULL columns. Everything else scans only the
requested book (the `book_id` indexes already isolate it), so no new index was justified: the slowest
plans are single-digit-to-tens of milliseconds for a book 10-100× larger than a typical one, and a
trigram index for substring search would need the `pg_trgm` extension. Revisit if substring search on
very large books becomes hot.
