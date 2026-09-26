package com.axel.pennywise.domain.transaction.query.ai;

import com.axel.pennywise.domain.transaction.query.TxField;
import java.util.EnumSet;
import java.util.Set;

/**
 * The fields the AI proposer may use: a deliberate subset of {@link TxField}. Audit/identity
 * fields (id, occurredAt, createdAt, updatedAt, externalId) add nothing to a natural-language
 * filter and only widen what a prompt-injected question could target, so they are left out here
 * even though {@link com.axel.pennywise.domain.transaction.query.QueryRequestParser} would accept
 * them from the manual advanced builder.
 */
final class AiFilterFields {

  static final Set<TxField> ALLOWED =
      EnumSet.of(
          TxField.TYPE,
          TxField.AMOUNT_MINOR,
          TxField.OCCURRED_ON,
          TxField.CATEGORY_ID,
          TxField.CATEGORY_NAME,
          TxField.PAYMENT_METHOD,
          TxField.TITLE,
          TxField.NOTE,
          TxField.DESCRIPTION);

  /** Above this, the category list no longer fits a bounded prompt; fail with a clear reason. */
  static final int MAX_CATEGORIES_IN_PROMPT = 150;

  private AiFilterFields() {}
}
