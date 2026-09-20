package com.axel.pennywise.domain.transaction.query;

import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.book.BookRepository;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deterministic ledger used by the search/analyze/export integration tests. Rows are inserted with
 * JDBC so {@code created_at} is fixed (base + insertion index seconds) and every figure below can
 * be checked against a hand calculation.
 *
 * <p>Book A (user A, UTC), 18 months 2025-01 .. 2026-06:
 *
 * <pre>
 *  every month i=0..17: Salary INCOME 500000 on the 1st (BANK_TRANSFER)
 *                       Rent EXPENSE 120000 on the 3rd (no payment method)
 *                       Groceries EXPENSE 4500 on the 10th (CARD) and 4500 on the 20th (no method)
 *  i%3==0 (6 months):   Freelance INCOME 25000 on the LAST day of the month (WALLET)
 *  2025-05-05/15/25:    3 x Old Gym EXPENSE 7700 (CASH, occurred_at null), category is disabled AND
 *                       soft-deleted but still referenced by these active rows
 *  2026-03-01..26:      130 x Coffee EXPENSE 350, five per day (equal amounts), CARD on even k
 *  3 soft-deleted rows: Groceries EXPENSE 999999 (must never appear anywhere)
 *
 *  income  = 18*500000 + 6*25000            = 9,150,000
 *  expense = 18*(120000+9000) + 23100 + 45500 = 2,390,600
 *  net     = 6,759,400        count = 72 + 6 + 3 + 130 = 211
 *  2025 alone: income 6,100,000 expense 1,571,100 count 55
 *  2026 alone: income 3,050,000 expense   819,500 count 156
 * </pre>
 *
 * Book A2 (user A): tiny text-matching fixture. Book B (user B): same category names, amounts of
 * 111111, to prove nothing leaks across tenants.
 */
final class QueryTestData {

  static final long INCOME_ALL = 9_150_000;
  static final long EXPENSE_ALL = 2_390_600;
  static final long COUNT_ALL = 211;

  final String subjectA = "q-user-a-" + UUID.randomUUID();
  final String subjectB = "q-user-b-" + UUID.randomUUID();
  final UUID bookA;
  final UUID bookText;
  final UUID bookB;
  final UUID salary;
  final UUID freelance;
  final UUID rent;
  final UUID groceries;
  final UUID coffee;
  final UUID oldGym;
  final UUID bCategory;

  private final JdbcTemplate jdbc;
  private final BookRepository books;
  private final CategoryRepository categoryRepo;
  private final UserEntity userA;
  private int seq = 0;

  QueryTestData(
      UserRepository users,
      BookRepository books,
      CategoryRepository categories,
      JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.books = books;
    this.categoryRepo = categories;
    userA = user(users, subjectA);
    UserEntity userB = user(users, subjectB);
    bookA = book(books, userA, "Main");
    bookText = book(books, userA, "Text");
    bookB = book(books, userB, "Other tenant");

    salary = category(categories, bookA, CategoryType.INCOME, "Salary");
    freelance = category(categories, bookA, CategoryType.INCOME, "Freelance");
    rent = category(categories, bookA, CategoryType.EXPENSE, "Rent");
    groceries = category(categories, bookA, CategoryType.EXPENSE, "Groceries");
    coffee = category(categories, bookA, CategoryType.EXPENSE, "Coffee");
    oldGym = category(categories, bookA, CategoryType.EXPENSE, "Old Gym");
    bCategory = category(categories, bookB, CategoryType.EXPENSE, "Groceries");
    UUID misc = category(categories, bookText, CategoryType.EXPENSE, "Misc");

    // historical category: disabled + soft-deleted, still referenced by active rows below
    jdbc.update(
        "update expense_tracker.categories set is_disabled = true, deleted_at = now() where id = ?",
        oldGym);

    for (int i = 0; i < 18; i++) {
      YearMonth ym = YearMonth.of(2025, 1).plusMonths(i);
      tx(bookA, salary, "INCOME", 500_000, ym.atDay(1), "Salary", null, "BANK_TRANSFER", null);
      tx(bookA, rent, "EXPENSE", 120_000, ym.atDay(3), "Rent", "monthly rent", null, null);
      tx(
          bookA,
          groceries,
          "EXPENSE",
          4_500,
          ym.atDay(10),
          "Groceries",
          "weekly shop",
          "CARD",
          null);
      tx(bookA, groceries, "EXPENSE", 4_500, ym.atDay(20), "Groceries", null, null, null);
      if (i % 3 == 0) {
        tx(
            bookA,
            freelance,
            "INCOME",
            25_000,
            ym.atEndOfMonth(),
            "Freelance gig",
            "invoice",
            "WALLET",
            null);
      }
    }
    for (int d : new int[] {5, 15, 25}) {
      tx(bookA, oldGym, "EXPENSE", 7_700, LocalDate.of(2025, 5, d), "Gym", null, "CASH", "no-time");
    }
    for (int k = 0; k < 130; k++) {
      tx(
          bookA,
          coffee,
          "EXPENSE",
          350,
          LocalDate.of(2026, 3, 1).plusDays(k / 5),
          "Coffee",
          null,
          k % 2 == 0 ? "CARD" : null,
          null);
    }
    for (int d : new int[] {15, 16, 17}) {
      tx(
          bookA,
          groceries,
          "EXPENSE",
          999_999,
          LocalDate.of(2025, 6, d),
          "Deleted",
          null,
          null,
          "deleted");
    }
    // April 2025 budgets (full-month targets)
    budget(bookA, groceries, LocalDate.of(2025, 4, 1), 40_000);
    budget(bookA, rent, LocalDate.of(2025, 4, 1), 120_000);

    // Book B: same shapes, different tenant
    for (int i = 0; i < 5; i++) {
      tx(
          bookB,
          bCategory,
          "EXPENSE",
          111_111,
          LocalDate.of(2025, 4, 10 + i),
          "Groceries",
          "Rent",
          "CARD",
          null);
    }

    // Book A2: text matching
    text(misc, "100% organic", null);
    text(misc, "100 organic", null);
    text(misc, "a_b", null);
    text(misc, "axb", null);
    text(misc, "path\\dir", null);
    text(misc, "pathdir", null);
    text(misc, null, "Has Percent 50%");
    text(misc, "UPPER Case", null);
    text(misc, null, null);
  }

  /** Extra book owned by user A for tests that need their own volume or amounts. */
  UUID newBookForA(String name) {
    return book(books, userA, name);
  }

  UUID newCategoryFor(UUID book, CategoryType type, String name) {
    return category(categoryRepo, book, type, name);
  }

  private void text(UUID category, String title, String note) {
    tx(bookText, category, "EXPENSE", 100, LocalDate.of(2025, 1, 1), title, note, null, null);
  }

  void tx(
      UUID book,
      UUID category,
      String type,
      long amount,
      LocalDate on,
      String title,
      String note,
      String paymentMethod,
      String flag) {
    Instant created = Instant.parse("2025-01-01T00:00:00Z").plusSeconds(seq++);
    boolean deleted = "deleted".equals(flag);
    boolean noTime = "no-time".equals(flag);
    jdbc.update(
        "insert into expense_tracker.transactions (id, book_id, type, amount_minor, occurred_on,"
            + " category_id, note, title, payment_method, occurred_at, created_at, updated_at,"
            + " deleted_at, version) values (?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " case when ? then null else (?::date + time '12:00') at time zone 'UTC' end,"
            + " ?::timestamptz, ?::timestamptz, case when ? then ?::timestamptz else null end, 0)",
        UUID.randomUUID(),
        book,
        type,
        amount,
        java.sql.Date.valueOf(on),
        category,
        note,
        title,
        paymentMethod,
        noTime,
        on.toString(),
        created.toString(),
        created.toString(),
        deleted,
        created.toString());
  }

  private void budget(UUID book, UUID category, LocalDate month, long amount) {
    jdbc.update(
        "insert into expense_tracker.budgets (id, book_id, category_id, month_start, amount_minor)"
            + " values (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        book,
        category,
        java.sql.Date.valueOf(month),
        amount);
  }

  private static UserEntity user(UserRepository users, String subject) {
    UserEntity u = new UserEntity();
    u.setAuthSubject(subject);
    return users.save(u);
  }

  private static UUID book(BookRepository books, UserEntity owner, String name) {
    BookEntity b = new BookEntity();
    b.setOwner(owner);
    b.setName(name);
    b.setCurrencyCode("USD");
    b.setTimezone("UTC");
    b.setOpeningBalanceMinor(1_000_000L); // must never leak into filtered net
    return books.save(b).getId();
  }

  private UUID category(CategoryRepository categories, UUID book, CategoryType type, String name) {
    CategoryEntity c = new CategoryEntity();
    c.setBook(books.getReferenceById(book));
    c.setType(type);
    c.setName(name);
    return categories.save(c).getId();
  }
}
