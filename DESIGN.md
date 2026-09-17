# Design: cold-start/logout fix + read-endpoint caching

Branch: `feature/coldstart-and-read-caching` (mirrored in `pennywise-mobile`).
Two unrelated fixes tracked under one branch per request.

## 1. Mobile: cold-start-aware networking + no false logout (pennywise-mobile repository)

Status: investigated, plan proposed, **not yet implemented** (pending confirmation).

### Findings

- `src/shared/api/client.ts`: default timeout 15s, uniform for all requests. On
  `ECONNABORTED`/`ETIMEDOUT` the response interceptor retries once with a 45s timeout
  (`COLD_START_RETRY_TIMEOUT_MS`) — already merged from `fix/backend-cold-start-handling`.
  Retry fires for _any_ timed-out request, not just the first request after app
  resume/foreground, so a genuinely dead connection also pays ~60s before failing.
- `src/shared/api/errors.ts:getApiErrorMessage`: already distinguishes timeout
  ("Server is taking longer than usual...") from generic no-response failures
  ("Can't connect to server. Check your internet connection."). One shared path,
  used by feature stores — not scattered.
- `apiClient`'s response interceptor (`client.ts:65-74`) correctly scopes logout to
  `err.response?.status === 401` only — timeout has no `err.response`, so it does not
  trigger `handleUnauthorized()`. This path is fine as-is.
- **Bug**: `src/features/auth/store.ts:203-225`, `validateSession()`. Catches _any_
  error from `getMe()` — timeout, network-unreachable, or 401 alike — and
  unconditionally calls `logout()`, which wipes `SecureStore` token + all local
  feature stores (`clearAccountState()`). Called once on app bootstrap
  (`src/app/_layout.tsx:142-149`). A cold backend timing out on the first `getMe()`
  call after launch silently logs the user out and wipes local data, despite a valid
  token.
- No `AppState` listener anywhere in the repository. No on-device network-reachability
  check (no NetInfo/expo-network dependency).

### Proposed fix

1. `validateSession()`: only logout on real auth failure (401/403). Timeout/network
   errors keep `sessionStatus: "authenticated"` (using the persisted `user`) and let
   the UI retry. Needs a shared `isAuthError(err)` helper alongside the existing
   `isTimeout()` in `client.ts`.
2. Scope the long retry timeout to the first request after resume, not every timed-out
   request: add an `AppState` listener that flags the next request as "possibly cold"
   after backgrounded → foregrounded past an idle threshold. Only that flagged request
   gets the 15s → 45s retry ladder; steady-state failures fail fast (~15s).
3. Retry copy: "Server is waking up, retrying…" while the cold-start retry is in
   flight; reserve "Can't connect to server. Check your internet connection." for
   cases with no cold-start signal, ideally gated on an actual on-device offline
   check (requires adding `@react-native-community/netinfo` or `expo-network` —
   neither exists in the repository today).
4. Genuine 401/403 still logs out — unaffected, since the interceptor already
   handles that correctly.

Open question before implementing: confirm scope of point 2/3 (AppState listener +
netinfo dependency) vs a smaller diff that only fixes point 1 (the actual silent-logout
bug) and leaves timeout-scoping as-is.

## 2. Backend: caching for hot read endpoints (pennywise repository)

Status: investigated, plan proposed, **not yet implemented**.

### Hot-path findings (from mobile `shared/api/*.ts` call sites)

| Endpoint                                 | Called from (mobile)                               | Frequency                                                        |
| ---------------------------------------- | -------------------------------------------------- | ---------------------------------------------------------------- |
| `GET /v1/books/{bookId}/balance`         | `home.tsx` on mount                                | every home-tab visit                                             |
| `GET /v1/books/{bookId}/summary/monthly` | `home.tsx`, `analytics.tsx` (x2), `categories.tsx` | every visit to 3 different tabs                                  |
| `GET /v1/books/{bookId}/summary/range`   | `transactions.tsx`, on-demand (date range picker)  | on-demand, high key cardinality                                  |
| `GET /v1/books/{bookId}/transactions`    | `transactions/store.ts`, on mount + pagination     | frequent, but cursor+filter params give near-unbounded key space |
| `GET /v1/books`                          | `books/store.ts`, on mount                         | once per session typically                                       |
| `GET /v1/books/{bookId}/categories`      | `categories/store.ts`, on mount                    | once per session/book-switch                                     |
| `GET /v1/books/{bookId}/budgets`         | `budgets/store.ts`, on mount, per month            | once per month view                                              |

**Recommendation**: cache `balance`, `summary/monthly`, `categories` list, `books`
list, `budgets` list. **Skip** caching the raw transaction list (cursor + filter
params give an effectively unbounded key space — poor hit rate, not worth the risk).
`summary/range` is optional/lower priority — bounded to a book but still
higher-cardinality than `monthly`; can be added later with the same eviction
mechanism if it proves hot.

### Cache backend

No Redis or cache provider is currently provisioned (`build.gradle` has no
`spring-boot-starter-cache`/caffeine/redis dependency; no Render add-on or multi-instance
config in `DEPLOYMENT.md`; Hikari pool max is 3, consistent with a single small
instance). **Caffeine (in-memory)**, via `spring-boot-starter-cache` +
`com.github.ben-manes.caffeine:caffeine`. Redis would add an external dependency and
network hop for no benefit on a single-instance deployment — only worth it if this
service ever scales to multiple instances (cache-consistency across instances would
then require it).

### TTL vs invalidation

Invalidation-driven, not time-driven. A stale balance/summary is a worse UX than a
slightly slower request on a financial app. Add a generous `expireAfterWrite` safety-net
TTL (e.g. 60 min) per cache purely as a ceiling in case an eviction path is ever missed
or data changes outside these code paths — never relied on for correctness.

### Cache keys (per-book scoped, mandatory)

- `bookBalance`: key = `bookId`
- `monthlySummary`: key = `bookId + ':' + yearMonth`
- `categories`: key = `bookId`
- `books`: key = `ownerId` (user ID)
- `budgets`: key = `bookId + ':' + yearMonth`

Cache at the service layer (`SummaryService`, `BookService`, `BudgetService`), not the
controller layer, since those methods already take the real key material (`BookEntity`,
`UserEntity`) as parameters. One exception: `CategoryController.list()` currently
inlines the repository query directly in the controller — needs a small `CategoryService.list(book)`
extraction (mirroring the service pattern every other domain already uses) so
`@Cacheable(key = "#book.id")` has a clean method to attach to. This isn't a new
abstraction, it's filling in the one domain that's inconsistent with the existing
pattern.

### Invalidation — exact trigger points

Root-caused at the shared service methods so every caller (including import, which
reuses `TransactionService.create`) is covered by one guard, not one per caller:

- `TransactionService.create/update/softDelete` (covers manual create, edit, delete,
  **and** import — `TransactionImportService` calls `TransactionService.create` per
  row) → evict `bookBalance[bookId]` **and all** `monthlySummary` entries for that
  `bookId` (not just one month — an update can move `occurredOn` across a month
  boundary, and import can touch many months at once, so per-month targeting risks
  missing an entry). This needs a small `CacheEvictionService.evictBook(bookId)`
  helper that walks the Caffeine `asMap()` for the `monthlySummary` cache and removes
  every key with that `bookId` prefix — plain `@CacheEvict` can't express "evict all
  keys matching this prefix," so this one piece is actual code, not just an
  annotation.
- `CategoryController.patch` — **only** when `name` changes (name appears in
  `CategoryBreakdownItem` inside monthly/range summaries) → evict `categories[bookId]`
  **and** run the same book-wide `monthlySummary` eviction as above. `icon`/`color`/
  `isDisabled` changes don't appear in any cached response — evict `categories[bookId]`
  only.
- `CategoryController.create` / `delete` → evict `categories[bookId]` only. Create
  can't affect existing summaries (no transactions reference the new category yet);
  delete is blocked by the controller while the category has active transactions, so
  by the time delete succeeds, no summary references it either.
- `BudgetService.upsert` / `delete` → evict `monthlySummary[bookId:month]` (precise —
  month is already an explicit request param here, unlike the transaction-write case)
  **and** `budgets[bookId:month]`.
- `BookService.create` → evict `books[ownerId]` (new book must appear in the list).
  No summary eviction needed — nothing is cached for a book that didn't exist yet.
- `BookService.updateName` → evict `books[ownerId]` only (name isn't part of any
  summary/balance response).
- `BookService.softDelete` → evict `books[ownerId]`. Its `bookBalance`/`monthlySummary`
  entries are left to expire via the safety-net TTL — harmless, since a soft-deleted
  book (already required to have zero transactions to delete) will never be queried
  again.

### Memory footprint (512 MB Render free-tier ceiling)

Each cached value is small (a handful of numbers + a short category-breakdown list,
low hundreds of bytes to a few KB). Cap every named cache with Caffeine's
`maximumSize` (e.g. 500 entries for `bookBalance`/`categories`/`budgets`/`books`, 300
for `monthlySummary` given the extra month dimension) — worst case low single-digit
MB across all caches combined, negligible against 512 MB. `maximumSize` uses
Caffeine's built-in LRU-ish eviction (W-TinyLFU) so it degrades gracefully instead of
OOMing if the entry count assumption is ever wrong.

### Confidence flags (per the "correctness over performance" instruction)

- **Confident**: `bookBalance`, `budgets`, `books`, `categories` eviction — each has a
  single precise key or a small, fully-enumerable set of write paths.
- **Needs the custom `CacheEvictionService` prefix-evict, not just annotations**:
  `monthlySummary` eviction after any transaction write or category rename. This is
  the one piece not implementable as a bare `@CacheEvict` and the one most worth
  scrutinizing in review before it ships.
- **Deliberately not caching**: transaction list endpoint (unbounded filter/cursor key
  space) and `summary/range` (optional, higher-cardinality than `monthly` — can reuse
  the same book-wide prefix-evict mechanism later if added).
