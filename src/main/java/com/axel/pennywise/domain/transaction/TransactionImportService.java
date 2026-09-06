package com.axel.pennywise.domain.transaction;

import com.axel.pennywise.api.dto.transaction.ImportResult;
import com.axel.pennywise.api.dto.transaction.ImportRowError;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryService;
import com.axel.pennywise.domain.category.CategoryType;
import com.axel.pennywise.exception.ApiException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionImportService {

  static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
  static final int MAX_DATA_ROWS = 20_000;
  private static final int MAX_TITLE_LENGTH = 120;
  private static final int MAX_NOTE_LENGTH = 280;

  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ISO_LOCAL_DATE,
          DateTimeFormatter.ofPattern("M/d/yyyy"),
          DateTimeFormatter.ofPattern("d/M/yyyy"),
          DateTimeFormatter.ofPattern("yyyy/MM/dd"),
          DateTimeFormatter.ofPattern("MM-dd-yyyy"));

  private static final List<DateTimeFormatter> TIME_FORMATS =
      List.of(DateTimeFormatter.ofPattern("H:mm"), DateTimeFormatter.ofPattern("HH:mm"));

  private final CategoryService categoryService;
  private final TransactionRepository txRepo;
  private final TransactionService txService;

  @Transactional
  public ImportResult importXlsx(BookEntity book, MultipartFile file) {
    validateFile(file);

    Counters counters = new Counters();
    List<ImportRowError> errors = new ArrayList<>();
    Set<String> categoriesCreated = new LinkedHashSet<>();

    try {
      TransactionRowParser.parse(
          file.getInputStream(),
          MAX_DATA_ROWS,
          row -> processRow(book, row, counters, errors, categoriesCreated));
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CORRUPT_FILE", "Could not read .xlsx file");
    }

    return new ImportResult(
        counters.total,
        counters.imported,
        counters.blank,
        counters.duplicate,
        errors.size(),
        List.copyOf(categoriesCreated),
        errors);
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "No file uploaded");
    }
    String name = file.getOriginalFilename();
    if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "File must be .xlsx");
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      throw new ApiException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "FILE_TOO_LARGE",
          "File exceeds " + (MAX_FILE_BYTES / (1024 * 1024)) + "MB limit");
    }
  }

  private void processRow(
      BookEntity book,
      RawTransactionRow row,
      Counters counters,
      List<ImportRowError> errors,
      Set<String> categoriesCreated) {
    counters.total++;

    if (row.isBlank()) {
      counters.blank++;
      return;
    }

    List<String> missing = new ArrayList<>();
    if (isBlank(row.date())) missing.add("Date");
    if (isBlank(row.description())) missing.add("Description");
    if (isBlank(row.amount())) missing.add("Amount");
    if (isBlank(row.type())) missing.add("Type");
    if (!missing.isEmpty()) {
      errors.add(
          reject(
              row,
              "MISSING_REQUIRED_FIELD",
              "Missing required field(s): " + String.join(", ", missing)));
      return;
    }

    String description = row.description().trim();
    if (description.length() > MAX_TITLE_LENGTH) {
      errors.add(
          reject(
              row,
              "DESCRIPTION_TOO_LONG",
              "Description exceeds " + MAX_TITLE_LENGTH + " characters"));
      return;
    }

    LocalDate occurredOn;
    try {
      occurredOn = parseDate(row.date().trim());
    } catch (DateTimeParseException e) {
      errors.add(reject(row, "INVALID_DATE", "Invalid Date value: " + row.date()));
      return;
    }

    OffsetDateTime occurredAt = null;
    if (!isBlank(row.time())) {
      LocalTime time;
      try {
        time = parseTime(row.time().trim());
      } catch (DateTimeParseException e) {
        errors.add(reject(row, "INVALID_TIME", "Invalid Time value: " + row.time()));
        return;
      }
      occurredAt = occurredOn.atTime(time).atZone(zoneIdOrDefault(book)).toOffsetDateTime();
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(row.amount().trim());
    } catch (NumberFormatException e) {
      errors.add(reject(row, "INVALID_AMOUNT", "Invalid Amount value: " + row.amount()));
      return;
    }
    if (amount.scale() > 2) {
      errors.add(reject(row, "INVALID_AMOUNT", "Amount must have at most 2 decimal places"));
      return;
    }
    if (amount.signum() == 0) {
      errors.add(reject(row, "INVALID_AMOUNT", "Amount cannot be zero"));
      return;
    }

    TransactionType type = parseType(row.type().trim());
    if (type == null) {
      errors.add(reject(row, "INVALID_TYPE", "Type must be 'Expense' or 'Income'"));
      return;
    }

    boolean signMatchesType =
        (type == TransactionType.EXPENSE && amount.signum() < 0)
            || (type == TransactionType.INCOME && amount.signum() > 0);
    if (!signMatchesType) {
      errors.add(reject(row, "AMOUNT_TYPE_MISMATCH", "Amount sign does not match Type"));
      return;
    }

    if (!isBlank(row.currency())) {
      String currency = row.currency().trim().toUpperCase(Locale.ROOT);
      if (!currency.equals(book.getCurrencyCode())) {
        errors.add(
            reject(
                row,
                "CURRENCY_MISMATCH",
                "Currency "
                    + currency
                    + " does not match book currency "
                    + book.getCurrencyCode()));
        return;
      }
    }

    String note = isBlank(row.notes()) ? null : row.notes().trim();
    if (note != null && note.length() > MAX_NOTE_LENGTH) {
      errors.add(reject(row, "NOTE_TOO_LONG", "Notes exceeds " + MAX_NOTE_LENGTH + " characters"));
      return;
    }

    String categoryName = isBlank(row.category()) ? "Uncategorized" : row.category().trim();
    CategoryType categoryType = CategoryType.valueOf(type.name());
    CategoryService.CategoryLookupResult lookup =
        categoryService.getOrCreateForImport(book, categoryType, categoryName);
    CategoryEntity category = lookup.category();
    if (lookup.created()) {
      categoriesCreated.add(category.getName());
    }

    PaymentMethod paymentMethod = parsePaymentMethod(row.paymentMethod());

    long amountMinor =
        amount.abs().setScale(2, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();

    String externalId = isBlank(row.externalId()) ? null : row.externalId().trim();
    if (externalId != null && isDuplicate(book, externalId)) {
      counters.duplicate++;
      return;
    }

    txService.create(
        book,
        category,
        type,
        amountMinor,
        occurredOn,
        note,
        description,
        paymentMethod,
        occurredAt,
        externalId);
    counters.imported++;
  }

  private ImportRowError reject(RawTransactionRow row, String code, String message) {
    return new ImportRowError(row.rowNumber(), code, message);
  }

  private boolean isDuplicate(BookEntity book, String externalId) {
    if (txRepo.existsByBook_IdAndExternalIdAndDeletedAtIsNull(book.getId(), externalId)) {
      return true;
    }
    return asUuid(externalId)
        .map(id -> txRepo.existsByBook_IdAndIdAndDeletedAtIsNull(book.getId(), id))
        .orElse(false);
  }

  private Optional<UUID> asUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private LocalDate parseDate(String raw) {
    for (DateTimeFormatter f : DATE_FORMATS) {
      try {
        return LocalDate.parse(raw, f);
      } catch (DateTimeParseException ignored) {
        // try next format
      }
    }
    if (raw.matches("\\d+(\\.\\d+)?")) {
      double serial = Double.parseDouble(raw);
      if (DateUtil.isValidExcelDate(serial)) {
        return DateUtil.getLocalDateTime(serial).toLocalDate();
      }
    }
    throw new DateTimeParseException("Unparseable date", raw, 0);
  }

  private LocalTime parseTime(String raw) {
    for (DateTimeFormatter f : TIME_FORMATS) {
      try {
        return LocalTime.parse(raw, f);
      } catch (DateTimeParseException ignored) {
        // try next format
      }
    }
    if (raw.matches("0(\\.\\d+)?|1(\\.0+)?")) {
      double fraction = Double.parseDouble(raw);
      int totalMinutes = (int) Math.round(fraction * 24 * 60);
      return LocalTime.of((totalMinutes / 60) % 24, totalMinutes % 60);
    }
    throw new DateTimeParseException("Unparseable time", raw, 0);
  }

  private TransactionType parseType(String raw) {
    String normalized = raw.toUpperCase(Locale.ROOT);
    if (normalized.equals("EXPENSE")) return TransactionType.EXPENSE;
    if (normalized.equals("INCOME")) return TransactionType.INCOME;
    return null;
  }

  private PaymentMethod parsePaymentMethod(String raw) {
    if (isBlank(raw)) return null;
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    for (PaymentMethod pm : PaymentMethod.values()) {
      if (pm.name().equals(normalized)) return pm;
    }
    return PaymentMethod.OTHER;
  }

  private ZoneId zoneIdOrDefault(BookEntity book) {
    try {
      return ZoneId.of(book.getTimezone());
    } catch (Exception e) {
      return ZoneId.of("UTC");
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static final class Counters {
    int total;
    int imported;
    int blank;
    int duplicate;
  }
}
