package com.axel.pennywise.domain.book;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BookMigrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void upgradesPopulatedV7WithoutChangingExistingData() {
    var ds =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    var jdbc = new JdbcTemplate(ds);
    Flyway.configure()
        .dataSource(ds)
        .schemas("expense_tracker")
        .defaultSchema("expense_tracker")
        .target("7")
        .load()
        .migrate();
    jdbc.execute(
        """
        INSERT INTO expense_tracker.users(id,auth_subject)
        SELECT ('00000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid, 'migration-' || i
        FROM generate_series(1,2) i
        """);
    jdbc.execute(
        """
        INSERT INTO expense_tracker.books(id,owner_user_id,name,currency_code,timezone,opening_balance_minor,created_at,updated_at,version,deleted_at)
        SELECT ('10000000-0000-0000-0000-' || lpad(i::text,12,'0'))::uuid,
          ('00000000-0000-0000-0000-' || lpad((1 + (i-1)/3)::text,12,'0'))::uuid,
          'Book ' || i,'USD','UTC',i*100,CASE WHEN i=3 THEN '2019-01-01'::timestamptz ELSE '2020-01-01'::timestamptz END,'2020-02-01',7,
          CASE WHEN i=3 THEN '2020-03-01'::timestamptz ELSE NULL END
        FROM generate_series(1,6) i
        """);
    jdbc.execute(
        """
        INSERT INTO expense_tracker.categories(id,book_id,type,name)
        VALUES ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','EXPENSE','Food');
        INSERT INTO expense_tracker.transactions(book_id,category_id,type,amount_minor,occurred_on)
        VALUES ('10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','EXPENSE',125,'2020-01-01');
        INSERT INTO expense_tracker.budgets(book_id,category_id,month_start,amount_minor)
        VALUES ('10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','2020-01-01',500);
        INSERT INTO expense_tracker.export_jobs(book_id,requested_by_user_id,status)
        VALUES ('10000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','PENDING');
        """);
    String originalColumns =
        "id,owner_user_id,name,currency_code,timezone,opening_balance_minor,created_at,updated_at,deleted_at,version";
    var before =
        jdbc.queryForList("SELECT " + originalColumns + " FROM expense_tracker.books ORDER BY id");
    var tables = List.of("transactions", "categories", "budgets", "export_jobs");
    var children =
        tables.stream().map(t -> jdbc.queryForList("SELECT * FROM expense_tracker." + t)).toList();
    Flyway.configure()
        .dataSource(ds)
        .schemas("expense_tracker")
        .defaultSchema("expense_tracker")
        .load()
        .migrate();
    assertEquals(
        before,
        jdbc.queryForList("SELECT " + originalColumns + " FROM expense_tracker.books ORDER BY id"));
    for (int i = 0; i < tables.size(); i++) {
      assertEquals(
          children.get(i), jdbc.queryForList("SELECT * FROM expense_tracker." + tables.get(i)));
    }
    assertEquals(
        List.of(1L, 2L, 0L, 0L, 1L, 2L),
        jdbc.queryForList("SELECT sort_order FROM expense_tracker.books ORDER BY id", Long.class));
    assertEquals(
        6,
        jdbc.queryForObject(
            "SELECT count(*) FROM expense_tracker.books WHERE icon='book' AND color='green'",
            Integer.class));
    jdbc.execute(
        """
        INSERT INTO expense_tracker.books(owner_user_id,name,currency_code,timezone)
        VALUES ('00000000-0000-0000-0000-000000000001','Legacy','usd','UTC')
        """);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM expense_tracker.books WHERE name='Legacy' AND icon='book' AND"
                + " color='green' AND sort_order=0",
            Integer.class));
    jdbc.execute(
        """
        INSERT INTO expense_tracker.books(owner_user_id,name,currency_code,timezone,created_at)
        SELECT '00000000-0000-0000-0000-000000000001','Plan ' || i,'USD','UTC',
          '2020-01-01'::timestamptz + i * interval '1 second'
        FROM generate_series(1,10000) i
        """);
    jdbc.execute("ANALYZE expense_tracker.books");
    var plan =
        jdbc.queryForList(
            "EXPLAIN (ANALYZE, BUFFERS) SELECT id,row_number() OVER (PARTITION BY owner_user_id"
                + " ORDER BY created_at,id)-1 FROM expense_tracker.books",
            String.class);
    System.out.println("Book backfill ranking plan: " + plan);
    assertTrue(plan.stream().anyMatch(line -> line.contains("WindowAgg")));
  }
}
