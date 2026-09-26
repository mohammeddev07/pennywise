# AI filter proposals ("Describe your filter")

Natural-language input to a P1 filter proposal. `POST /v1/books/{bookId}/filter-proposals`
turns a <=500 char question into a filter the user reviews/edits in the normal Advanced
builder before Apply. See `domain/transaction/query/ai/` for the implementation and
`domain/transaction/query/` (`TxField`, `FilterOperator`, `QueryRequestParser`) for the P1
AST/validator this reuses - there is no second query language and no direct database/SQL
access for the model.

## Provider setup

- Provider: Google Gemini, model `gemini-2.5-flash-lite` (configurable, see below), called via
  `generateContent` with `responseMimeType: application/json` + a bounded `responseSchema`
  (structured output). No SDK/LangChain/Spring AI - a single Spring-autoconfigured `RestClient`
  call in `GeminiFilterClient`.
- Get an API key from Google AI Studio (https://aistudio.google.com/apikey). This key is a
  **secret**: it lives only in server env vars, never in an `EXPO_PUBLIC_*` variable, and is
  never returned to the client or logged.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `AI_FILTERS_ENABLED` | `false` | Feature flag / kill switch. Off by default. |
| `GEMINI_API_KEY` | *(empty)* | Required for the feature to actually run. |
| `AI_MODEL` | `gemini-2.5-flash-lite` | Model id passed to `generateContent`. |
| `AI_GEMINI_TIMEOUT_SECONDS` | `10` | Connect + read timeout on the Gemini call. |
| `AI_FILTER_RATE_PER_MINUTE` | `10` | Per-user in-memory burst limit. |
| `AI_FILTER_RATE_PER_DAY` | `100` | Per-user atomic DB quota (see below). |

The feature is only live when **both** `AI_FILTERS_ENABLED=true` and `GEMINI_API_KEY` is set;
either being absent returns `503 AI_FILTERS_DISABLED` and manual filters are unaffected.

## Feature flag and kill switch

`AI_FILTERS_ENABLED` is the hard kill switch: set it to `false` and redeploy/restart to turn
the feature off instantly, independent of the Gemini-side spending controls below. There is no
runtime-refreshable config in this app (no Spring Cloud Config / Actuator `/refresh`), so
flipping it requires a restart - the same as every other `@Value`-backed setting here (e.g.
`app.security.auth-enabled`).

**Also configure, outside this repo:**
- A budget/usage alert in Google Cloud / AI Studio billing for the API key's project. This is a
  notification, not a cap - it does not stop calls once tripped.
- The daily-quota table (`ai_filter_quota`, below) is the actual hard cap on call volume per
  user; there is currently no global (all-users) spend cap beyond `AI_FILTER_RATE_PER_DAY x`
  number of active users. For a personal pilot with one or a handful of users this is
  sufficient; a multi-tenant rollout should add a global daily cap before enabling broadly.

## Cost estimate

Per call: system instruction (~300-600 tokens depending on category count) + a <=500 char
question (~150 tokens worst case) + up to 1024 output tokens (`maxOutputTokens`, enforced
server-side). At `gemini-2.5-flash-lite` list pricing (check
https://ai.google.dev/gemini-api/docs/pricing for current rates - this changes), that is a
small fraction of a cent per call. At the default `AI_FILTER_RATE_PER_DAY=100`, worst case is
~100 calls/user/day; a single personal-pilot user costs at most a few cents/day even at the cap.

## Quota / rate-limit behavior

Two independent layers, both checked before the Gemini call (so a rejected request never
spends quota or hits the provider):

1. **Per-minute** (`AiFilterRateLimiter`): an in-memory Caffeine counter per user, resets each
   minute. `ponytail`: single-instance and resets on restart - it smooths local bursts, it is
   not the safety-critical layer. Exceeding it: `429 AI_FILTER_RATE_LIMITED`, `Retry-After: 60`.
2. **Per-day** (`AiFilterQuotaService` / `ai_filter_quota` table, migration `V7`): an atomic
   `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE count < ? RETURNING count`. This is safe
   under concurrent requests and multiple app instances - there is no read-then-write gap.
   Exceeding it: `429 AI_FILTER_DAILY_LIMIT`, `Retry-After: <seconds to next UTC midnight>`.

The quota migration only adds this one small table; it does not touch transaction/ledger
schema.

## Disclosure

The mobile "Describe your filter" sheet shows, next to the input, every time (not just on
first use - simpler and always visible rather than a one-time toast that could be missed):

> Sent to our AI provider to build this filter, along with this book's category names. Review
> every condition before applying - this replaces your current filter, it does not merge with it.

Only the question text, the allowed field/operator schema, this book's category id/name/type
list, currency, minor-unit digits, timezone and the server-computed reference date are sent.
No ledger rows, balances, account email, auth tokens, other books, or transaction history are
sent. Category names and the question are treated as untrusted data in the prompt (see
"Prompt injection" below), not as instructions.

## Model selection

`gemini-2.5-flash-lite` was chosen for latency/cost on a small, fully-schema-constrained
structured-output task (this is not open-ended generation - the model only ever fills a fixed
JSON shape). `AI_MODEL` is configurable if a different Gemini model is preferred; no code
change is needed, only the env var, since the endpoint URL and request/response shape are the
same across `generateContent`-compatible Gemini models.

## Safety / validation

- The model's JSON is deserialized into `ProviderProposal` (a fixed, non-recursive schema: a
  question is either an EQ/IN/BETWEEN/etc. condition on one of 9 allowed fields, grouped at
  most two levels deep), never treated as SQL/executable text.
- `FilterProposalMapper` rebuilds it into the exact wire JSON `QueryRequestParser` already
  accepts from the manual advanced builder and runs it through **that same parser** - every
  existing check (unknown fields, illegal field/operator pairs, UUID format, depth/condition/
  IN-size limits, integer precision) applies unchanged.
- Category ids are additionally checked against this book's own category list (ownership),
  and every field is checked against the AI-allowed subset, as defense in depth on top of the
  schema already constraining the model to them.
- Amounts are converted from major to minor units using this book's own currency
  (`MoneyLimits.minorUnitDigits`); currency conversion is never performed - the prompt tells
  the model to keep amounts in the book's currency, and there is no code path that converts.
- Relative dates ("last month", "this year", ...) are resolved **server-side** from
  `LocalDate.now(bookTimezone)` via `DatePreset` - the model only names a preset, it never
  computes or is trusted with the actual date arithmetic.
- The human-readable summary is generated server-side from the validated AST
  (`FilterSummaryFormatter`), never taken from model prose.
- **Prompt injection**: the fixed instructions/schema live in Gemini's `systemInstruction`
  field, separate from the untrusted question (sent as the `user` turn) and from category
  names (also untrusted, since a user can name a category anything). This reduces but does not
  eliminate injection risk from a single LLM call - the real backstop is that whatever comes
  back must still pass the full validation above before it becomes an executable filter. Worst
  case from a successful injection is a wrong-but-valid PROPOSAL, which the user reviews before
  Apply; it can never become an empty/`match-everything` filter (an empty proposal is rejected,
  not treated as "show all"), a raw query, or a write.

## Known limitations (measured, not a roadmap)

- No CI provider-stub matrix or 20-30 question evaluation fixture has been built yet (the spec
  for this feature asked for one; see `FilterProposalMapperTest` for the unit coverage that
  does exist - category ownership, field allowlist, amount/date conversion, AND/OR/exclusion
  shapes). Treat the >=90%-of-clear-questions bar as **not yet measured** for this build; do
  not enable for real users beyond a personal pilot until that evaluation exists and passes.
- No WireMock/HTTP-level stub for `GeminiFilterClient` exists in this repo (no WireMock
  dependency was present before this feature); provider-error paths (timeout, 429, refusal,
  malformed JSON) are covered by manual code review of `GeminiFilterClient`, not by an
  automated stubbed-HTTP test yet.
- No global (cross-user) daily spend cap - see "Feature flag and kill switch" above.
- Week-based relative dates ("this week", "last week") are intentionally not offered as
  presets (only day/month/year), since the spec's examples only called out month/year
  boundaries; add a preset if that's needed.

## Rollback

Set `AI_FILTERS_ENABLED=false` (or unset `GEMINI_API_KEY`) and restart - the endpoint then
returns `503 AI_FILTERS_DISABLED` immediately, with zero effect on manual filters, search, or
analyze. No data migration is needed to roll back: `ai_filter_quota` is inert once the feature
is off and can be left in place or dropped independently at any time.
