package com.axel.pennywise.domain.transaction.query;

/**
 * Every operator the filter AST can carry; which ones a field accepts is decided by {@link
 * TxField}.
 */
public enum FilterOperator {
  EQ,
  NE,
  IN,
  NOT_IN,
  GT,
  GTE,
  LT,
  LTE,
  BETWEEN,
  CONTAINS,
  NOT_CONTAINS,
  STARTS_WITH,
  ENDS_WITH,
  IS_NULL,
  IS_NOT_NULL
}
