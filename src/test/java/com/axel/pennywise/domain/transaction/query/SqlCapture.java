package com.axel.pennywise.domain.transaction.query;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Records every SQL string Hibernate is about to send; wired through a test property. */
public class SqlCapture implements StatementInspector {

  static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

  @Override
  public String inspect(String sql) {
    STATEMENTS.add(sql);
    return sql;
  }
}
