/**
 * Shared contract for POST /v1/books/{bookId}/transactions/{search,analyze} and
 * /export/query. Mirrors docs/transaction-query.md - copy into pennywise-mobile
 * (e.g. src/shared/types/transactionQuery.ts) for P1.4.
 */

// ---------------------------------------------------------------- filter AST

export type GroupOp = 'AND' | 'OR';

export type FilterOperator =
  | 'EQ' | 'NE' | 'IN' | 'NOT_IN'
  | 'GT' | 'GTE' | 'LT' | 'LTE' | 'BETWEEN'
  | 'CONTAINS' | 'NOT_CONTAINS' | 'STARTS_WITH' | 'ENDS_WITH'
  | 'IS_NULL' | 'IS_NOT_NULL';

export type TransactionType = 'INCOME' | 'EXPENSE';
export type PaymentMethod = 'CASH' | 'CARD' | 'BANK_TRANSFER' | 'WALLET' | 'OTHER';

/** `book` is the endpoint scope, `deletedAt` is always null and `version` is a concurrency token: none is filterable. */
export type QueryField =
  | 'id' | 'type' | 'amountMinor' | 'occurredOn' | 'occurredAt' | 'categoryId'
  | 'categoryName' | 'paymentMethod' | 'title' | 'note' | 'description'
  | 'createdAt' | 'updatedAt' | 'externalId';

export interface FilterGroup {
  kind: 'group';
  op: GroupOp;
  children: FilterNode[];
}

export interface FilterCondition {
  kind: 'condition';
  field: QueryField;
  operator: FilterOperator;
  /**
   * uuid string | 'INCOME' | 'EXPENSE' | PaymentMethod | integer (minor units) | 'YYYY-MM-DD' |
   * ISO-8601 timestamp with offset | non-empty string (<= 280 chars).
   * IN / NOT_IN: non-empty array (<= 100). BETWEEN: [lower, upper], inclusive.
   * Omit for IS_NULL / IS_NOT_NULL.
   */
  value?: string | number | Array<string | number>;
}

export type FilterNode = FilterGroup | FilterCondition;

export interface SortKey {
  field: QueryField;
  direction: 'ASC' | 'DESC';
}

export interface TransactionQuery {
  /** Omitted or an empty top-level AND = all active rows of the book. */
  filter?: FilterNode;
  /** <= 3 keys. Default: occurredOn DESC, createdAt DESC, id DESC. `id` ASC is appended as tie-breaker. */
  sort?: SortKey[];
}

// ---------------------------------------------------------------- limits

export const QUERY_LIMITS = {
  maxDepth: 3,
  maxConditions: 30,
  maxInValues: 100,
  maxSortKeys: 3,
  maxTextChars: 280,
  maxBodyBytes: 64 * 1024,
  defaultLimit: 50,
  maxLimit: 200,
  maxOffset: 100_000,
  maxAnalysisWindowYears: 5,
  maxCategoryBucketCells: 20_000,
  maxExportRows: 50_000,
} as const;

// ---------------------------------------------------------------- capability matrix

export type FieldKind = 'UUID' | 'ENUM' | 'NUMBER' | 'DATE' | 'TIMESTAMP' | 'TEXT';

const BY_KIND: Record<FieldKind, FilterOperator[]> = {
  UUID: ['EQ', 'NE', 'IN', 'NOT_IN'],
  ENUM: ['EQ', 'NE', 'IN', 'NOT_IN'],
  NUMBER: ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN'],
  DATE: ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN'],
  TIMESTAMP: ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN'],
  TEXT: ['EQ', 'NE', 'CONTAINS', 'NOT_CONTAINS', 'STARTS_WITH', 'ENDS_WITH'],
};

export const FIELD_CAPABILITIES: Record<
  QueryField,
  { kind: FieldKind; nullable: boolean; sortable: true; operators: readonly FilterOperator[] }
> = Object.fromEntries(
  (
    [
      ['id', 'UUID', false], ['type', 'ENUM', false], ['amountMinor', 'NUMBER', false],
      ['occurredOn', 'DATE', false], ['occurredAt', 'TIMESTAMP', true], ['categoryId', 'UUID', false],
      ['categoryName', 'TEXT', false], ['paymentMethod', 'ENUM', true], ['title', 'TEXT', true],
      ['note', 'TEXT', true], ['description', 'TEXT', false], ['createdAt', 'TIMESTAMP', false],
      ['updatedAt', 'TIMESTAMP', false], ['externalId', 'TEXT', true],
    ] as Array<[QueryField, FieldKind, boolean]>
  ).map(([field, kind, nullable]) => [
    field,
    {
      kind,
      nullable,
      sortable: true,
      operators: nullable ? [...BY_KIND[kind], 'IS_NULL', 'IS_NOT_NULL'] : BY_KIND[kind],
    },
  ]),
) as never;

// ---------------------------------------------------------------- search

export interface SearchRequest extends TransactionQuery {
  page?: { offset?: number; limit?: number };
}

/** Same object as GET /v1/books/{bookId}/transactions/{id}. */
export interface TransactionItem {
  id: string;
  bookId: string;
  type: TransactionType;
  amountMinor: number;
  occurredOn: string;
  occurredAt: string | null;
  title: string | null;
  categoryId: string;
  category: { id: string; name: string; type: TransactionType };
  paymentMethod: PaymentMethod | null;
  note: string | null;
  externalId: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  version: number;
}

export interface SearchResponse {
  items: TransactionItem[];
  totalCount: number;
  page: { offset: number; limit: number; hasMore: boolean };
  /** Identifies filter + sort (not the page). Reset to offset 0 when it changes, after mutations and on refresh. */
  queryFingerprint: string;
}

// ---------------------------------------------------------------- analyze

export type Bucket = 'DAY' | 'MONTH' | 'YEAR';

export interface AnalyzeRequest {
  filter?: FilterNode;
  bucket: Bucket;
  /** Inclusive ledger dates (occurredOn), <= 5 years. */
  window: { startDate: string; endDate: string };
}

export interface CategoryTotal {
  categoryId: string;
  categoryName: string;
  type: TransactionType;
  totalMinor: number;
  count: number;
  /** Share of the FILTERED expense total (0-100, 2 decimals). null for income or a zero denominator. */
  percentOfExpense: number | null;
}

export interface BucketTotal {
  /** 'YYYY-MM-DD' | 'YYYY-MM' | 'YYYY' */
  key: string;
  /** Clipped to the window. */
  start: string;
  end: string;
  /** true when the calendar period is only partly inside the window - label it. */
  partial: boolean;
  incomeTotalMinor: number;
  expenseTotalMinor: number;
  netMinor: number;
  count: number;
}

export interface CategoryBucketCell {
  categoryId: string;
  type: TransactionType;
  bucketKey: string;
  totalMinor: number;
  count: number;
}

export interface AnalyzeResponse {
  bookId: string;
  currencyCode: string;
  bucket: Bucket;
  window: { startDate: string; endDate: string };
  /** filter AND occurredOn BETWEEN window - send it to search to get the same rows. */
  effectiveFilter: FilterNode;
  queryFingerprint: string;
  matchedCount: number;
  incomeTotalMinor: number;
  expenseTotalMinor: number;
  /** income - expense over the matches; the opening balance is NOT included. */
  netMinor: number;
  categories: CategoryTotal[];
  buckets: BucketTotal[];
  /** Dense (categories x buckets), zeros included. */
  categoryBuckets: CategoryBucketCell[];
  largestExpense: TransactionItem | null;
  /** Non-null only when the window is exactly one full calendar month. Full-month targets, not filtered allowances. */
  monthlyBudgets: {
    month: string;
    label: string;
    items: Array<{ categoryId: string; categoryName: string; amountMinor: number }>;
  } | null;
}

// ---------------------------------------------------------------- errors

export type QueryErrorCode =
  | 'VALIDATION_ERROR'
  | 'BAD_REQUEST'
  | 'PAGE_OFFSET_LIMIT_EXCEEDED'
  | 'NOT_FOUND'
  | 'REQUEST_TOO_LARGE'
  | 'ANALYSIS_TOO_LARGE'
  | 'AMOUNT_TOTAL_UNSUPPORTED'
  | 'EXPORT_TOO_LARGE';
