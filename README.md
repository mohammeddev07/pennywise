# pennywise
PennyWise - A not so boring cash management app. Track your spending and save smarter with a simple, clever vibe.

## Transaction dates and monthly totals

`transactions.occurred_on` is the canonical ledger date used by transaction date filters,
monthly summaries, and budget spend totals. An explicitly supplied `occurredOn` is treated as
a calendar date in the book's configured timezone. When a request supplies only `occurredAt`,
the backend converts that instant to the book timezone before deriving `occurredOn`. This keeps
summary and budget month boundaries aligned with the book's local calendar rather than UTC.
