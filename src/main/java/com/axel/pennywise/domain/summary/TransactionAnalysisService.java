package com.axel.pennywise.domain.summary;

import com.axel.pennywise.api.dto.transaction.TransactionAnalysisResponse;
import com.axel.pennywise.api.dto.transaction.TransactionAnalysisResponse.BucketItem;
import com.axel.pennywise.api.dto.transaction.TransactionAnalysisResponse.BudgetItem;
import com.axel.pennywise.api.dto.transaction.TransactionAnalysisResponse.CategoryBucketItem;
import com.axel.pennywise.api.dto.transaction.TransactionAnalysisResponse.CategoryTotalItem;
import com.axel.pennywise.api.dto.transaction.TransactionAnalysisResponse.MonthlyBudgets;
import com.axel.pennywise.api.dto.transaction.TransactionResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.budget.BudgetRepository;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.domain.common.MoneyLimits;
import com.axel.pennywise.domain.transaction.TransactionType;
import com.axel.pennywise.domain.transaction.query.FilterNode;
import com.axel.pennywise.domain.transaction.query.QueryFingerprint;
import com.axel.pennywise.domain.transaction.query.QueryRequestParser;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Analyze;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Bucket;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Window;
import com.axel.pennywise.domain.transaction.query.TransactionQueryRepository;
import com.axel.pennywise.domain.transaction.query.TransactionQueryRepository.AggregateRow;
import com.axel.pennywise.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filter-driven analytics. Semantics match the existing summary routes: amounts are positive minor
 * units split by INCOME/EXPENSE, {@code net = income - expense}, opening balance is never included.
 * The database does the heavy lifting once ({@code GROUP BY day, category, type} over every
 * matching row); calendar buckets and category-by-bucket cells are rolled up from those groups in
 * memory, so all figures are derived from a single consistent set. Results are intentionally not
 * cached: filter expressions are high-cardinality and the monthly/balance caches are unaffected.
 */
@Service
@RequiredArgsConstructor
public class TransactionAnalysisService {

  public static final int MAX_CATEGORY_BUCKET_CELLS = 20_000;
  static final String BUDGET_LABEL =
      "Full-month category budget targets for this calendar month. Not adjusted for the applied"
          + " filter.";

  private final TransactionQueryRepository repo;
  private final BudgetRepository budgetRepo;
  private final SummaryService summaryService;

  private record Period(String key, LocalDate start, LocalDate end, boolean partial) {}

  private record CatKey(UUID id, TransactionType type) {}

  private static final class Cat {
    final String name;
    long total;
    long count;
    long[] cellTotals;
    long[] cellCounts;

    Cat(String name) {
      this.name = name;
    }
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public TransactionAnalysisResponse analyze(BookEntity book, Analyze req) {
    Window window = req.window();
    summaryService.validateRangeOrThrow(window.startDate(), window.endDate());

    FilterNode effective = QueryRequestParser.withWindow(req.filter(), window);
    QueryRequestParser.requireWithinLimits(effective);
    UUID bookId = book.getId();

    List<AggregateRow> rows = repo.aggregate(bookId, effective);
    List<Period> periods = periods(req.bucket(), window);
    Map<String, Integer> periodIndex = new HashMap<>();
    for (int i = 0; i < periods.size(); i++) periodIndex.put(periods.get(i).key(), i);

    Map<CatKey, Cat> cats = new LinkedHashMap<>();
    for (AggregateRow r : rows)
      cats.computeIfAbsent(new CatKey(r.categoryId(), r.type()), k -> new Cat(r.categoryName()));
    if ((long) cats.size() * periods.size() > MAX_CATEGORY_BUCKET_CELLS) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "ANALYSIS_TOO_LARGE",
          "The result would need "
              + cats.size()
              + " categories x "
              + periods.size()
              + " buckets, above the limit of "
              + MAX_CATEGORY_BUCKET_CELLS
              + " cells. Narrow the window, pick a coarser bucket, or filter fewer categories.");
    }
    for (Cat c : cats.values()) {
      c.cellTotals = new long[periods.size()];
      c.cellCounts = new long[periods.size()];
    }

    long[] bIncome = new long[periods.size()];
    long[] bExpense = new long[periods.size()];
    long[] bCount = new long[periods.size()];
    long income = 0;
    long expense = 0;
    long matched = 0;
    for (AggregateRow r : rows) {
      long amount = exact(r.totalMinor());
      int b = periodIndex.get(key(req.bucket(), r.occurredOn()));
      Cat cat = cats.get(new CatKey(r.categoryId(), r.type()));
      cat.total = add(cat.total, amount);
      cat.count += r.count();
      cat.cellTotals[b] = add(cat.cellTotals[b], amount);
      cat.cellCounts[b] += r.count();
      bCount[b] += r.count();
      matched += r.count();
      if (r.type() == TransactionType.INCOME) {
        income = add(income, amount);
        bIncome[b] = add(bIncome[b], amount);
      } else {
        expense = add(expense, amount);
        bExpense[b] = add(bExpense[b], amount);
      }
    }

    List<Map.Entry<CatKey, Cat>> ordered = new ArrayList<>(cats.entrySet());
    ordered.sort(
        Comparator.comparingInt(
                (Map.Entry<CatKey, Cat> e) -> e.getKey().type() == TransactionType.EXPENSE ? 0 : 1)
            .thenComparing((Map.Entry<CatKey, Cat> e) -> -e.getValue().total)
            .thenComparing(e -> e.getValue().name.toLowerCase(Locale.ROOT))
            .thenComparing(e -> e.getKey().id()));

    List<CategoryTotalItem> categories = new ArrayList<>();
    List<CategoryBucketItem> cells = new ArrayList<>();
    for (var e : ordered) {
      CatKey k = e.getKey();
      Cat c = e.getValue();
      CategoryType ct = CategoryType.valueOf(k.type().name());
      categories.add(
          new CategoryTotalItem(
              k.id(), c.name, ct, c.total, c.count, percent(k.type(), c.total, expense)));
      for (int i = 0; i < periods.size(); i++) {
        cells.add(
            new CategoryBucketItem(
                k.id(), ct, periods.get(i).key(), c.cellTotals[i], c.cellCounts[i]));
      }
    }

    List<BucketItem> buckets = new ArrayList<>();
    for (int i = 0; i < periods.size(); i++) {
      Period p = periods.get(i);
      buckets.add(
          new BucketItem(
              p.key(),
              p.start(),
              p.end(),
              p.partial(),
              bIncome[i],
              bExpense[i],
              bIncome[i] - bExpense[i],
              bCount[i]));
    }

    TransactionResponse largest =
        repo.largestExpense(bookId, effective)
            .map(t -> TransactionResponse.from(t, bookId))
            .orElse(null);

    return new TransactionAnalysisResponse(
        bookId,
        book.getCurrencyCode(),
        req.bucket().name(),
        new TransactionAnalysisResponse.Window(window.startDate(), window.endDate()),
        effective.canonical(),
        QueryFingerprint.of(effective, null),
        matched,
        income,
        expense,
        income - expense,
        categories,
        buckets,
        cells,
        largest,
        monthlyBudgets(bookId, window));
  }

  /**
   * Budgets are whole-month targets: only offered when the window is exactly one calendar month.
   */
  private MonthlyBudgets monthlyBudgets(UUID bookId, Window w) {
    YearMonth ym = YearMonth.from(w.startDate());
    if (!w.startDate().equals(ym.atDay(1)) || !w.endDate().equals(ym.atEndOfMonth())) return null;
    List<BudgetItem> items =
        budgetRepo.findAllActiveForBookAndMonth(bookId, w.startDate()).stream()
            .map(
                b ->
                    new BudgetItem(
                        b.getCategory().getId(), b.getCategory().getName(), b.getAmountMinor()))
            .toList();
    return new MonthlyBudgets(ym.toString(), BUDGET_LABEL, items);
  }

  private static BigDecimal percent(TransactionType type, long total, long expenseTotal) {
    if (type != TransactionType.EXPENSE || expenseTotal <= 0) return null;
    return BigDecimal.valueOf(total)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(expenseTotal), 2, RoundingMode.HALF_UP);
  }

  static List<Period> periods(Bucket bucket, Window w) {
    List<Period> out = new ArrayList<>();
    LocalDate start = w.startDate();
    LocalDate end = w.endDate();
    switch (bucket) {
      case DAY -> {
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1))
          out.add(new Period(d.toString(), d, d, false));
      }
      case MONTH -> {
        for (YearMonth m = YearMonth.from(start);
            !m.isAfter(YearMonth.from(end));
            m = m.plusMonths(1)) {
          out.add(clipped(m.toString(), m.atDay(1), m.atEndOfMonth(), start, end));
        }
      }
      case YEAR -> {
        for (int y = start.getYear(); y <= end.getYear(); y++) {
          out.add(
              clipped(
                  String.valueOf(y), LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), start, end));
        }
      }
    }
    return out;
  }

  private static Period clipped(
      String key, LocalDate first, LocalDate last, LocalDate ws, LocalDate we) {
    LocalDate s = first.isBefore(ws) ? ws : first;
    LocalDate e = last.isAfter(we) ? we : last;
    return new Period(key, s, e, !s.equals(first) || !e.equals(last));
  }

  private static String key(Bucket bucket, LocalDate d) {
    return switch (bucket) {
      case DAY -> d.toString();
      case MONTH -> YearMonth.from(d).toString();
      case YEAR -> String.valueOf(d.getYear());
    };
  }

  /** Every figure must stay JS-safe; rows written before the aggregate cap could break that. */
  private static long exact(BigDecimal v) {
    try {
      return add(0, v.longValueExact());
    } catch (ArithmeticException e) {
      throw overflow();
    }
  }

  private static long add(long a, long b) {
    long sum;
    try {
      sum = Math.addExact(a, b);
    } catch (ArithmeticException e) {
      throw overflow();
    }
    if (sum > MoneyLimits.MAX_AGGREGATE_AMOUNT_MINOR) throw overflow();
    return sum;
  }

  private static ApiException overflow() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "AMOUNT_TOTAL_UNSUPPORTED",
        "A total exceeds "
            + MoneyLimits.MAX_AGGREGATE_AMOUNT_MINOR
            + " minor units and cannot be reported exactly. Narrow the filter or window.");
  }
}
