package com.axel.pennywise.domain.transaction.query.ai;

import com.axel.pennywise.api.dto.ai.FilterProposalResponse;
import com.axel.pennywise.domain.book.BookEntity;
import com.axel.pennywise.domain.category.CategoryEntity;
import com.axel.pennywise.domain.category.CategoryService;
import com.axel.pennywise.domain.common.MoneyLimits;
import com.axel.pennywise.domain.transaction.query.FilterOperator;
import com.axel.pennywise.domain.transaction.query.QuerySpec.Query;
import com.axel.pennywise.domain.transaction.query.TxField;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.exception.RateLimitExceededException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a "describe your filter" request: enabled/quota checks, prompt assembly, the Gemini
 * call, and mapping the result into a P1-validated proposal. Book ownership is the caller's job
 * (see {@code AiFilterController}); this class only needs an already-owned {@link BookEntity}.
 */
@Service
@RequiredArgsConstructor
public class FilterProposalService {

  private static final int MAX_MESSAGE_CHARS = 300;

  // All AI prompts live under src/main/resources/prompts/ as .md, not inline in Java, so
  // they're reviewable/editable without touching code. Loaded once; the template itself is
  // static, only the placeholders below vary per request.
  private static final String SYSTEM_INSTRUCTION_TEMPLATE =
      loadPrompt("prompts/filter-proposal-system-instruction.md");

  private static String loadPrompt(String classpathLocation) {
    try {
      return new ClassPathResource(classpathLocation).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Missing prompt resource: " + classpathLocation, e);
    }
  }

  @Value("${app.ai.filters-enabled:false}")
  private boolean filtersEnabled;

  private final CategoryService categoryService;
  private final GeminiFilterClient geminiClient;
  private final FilterProposalMapper mapper;
  private final FilterSummaryFormatter summaryFormatter;
  private final AiFilterRateLimiter rateLimiter;
  private final AiFilterQuotaService quotaService;

  public FilterProposalResponse propose(BookEntity book, UserEntity user, String text) {
    if (!filtersEnabled || !geminiClient.configured()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_FILTERS_DISABLED", "AI filters are not enabled");
    }

    List<CategoryEntity> categories = categoryService.list(book);
    if (categories.size() > AiFilterFields.MAX_CATEGORIES_IN_PROMPT) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "AI_FILTER_TOO_MANY_CATEGORIES",
          "This book has more than "
              + AiFilterFields.MAX_CATEGORIES_IN_PROMPT
              + " categories, too many for AI filtering right now. Use the manual filter builder.");
    }

    LocalDate referenceDate = LocalDate.now(zoneIdFor(book));

    if (!rateLimiter.tryReserve(user.getId())) {
      throw new RateLimitExceededException(
          "AI_FILTER_RATE_LIMITED", "Too many AI filter requests. Try again in a minute.", 60);
    }
    if (!quotaService.tryReserve(user.getId())) {
      throw new RateLimitExceededException(
          "AI_FILTER_DAILY_LIMIT",
          "Today's AI filter limit is used up. Try again tomorrow, or use the manual filter.",
          secondsUntilUtcMidnight());
    }

    String systemInstruction = buildSystemInstruction(book, categories, referenceDate);
    ProviderProposal proposal = geminiClient.propose(systemInstruction, text.trim());

    return switch (proposal.status() == null ? "" : proposal.status()) {
      case "CLARIFY" ->
          new FilterProposalResponse(
              "CLARIFY",
              null,
              null,
              null,
              safeMessage(
                  proposal.clarification(), "Could you rephrase that with more specific detail?"),
              null);
      case "UNSUPPORTED" ->
          new FilterProposalResponse(
              "UNSUPPORTED",
              null,
              null,
              null,
              null,
              safeMessage(
                  proposal.limitation(),
                  "That request isn't something this filter can express. Use the manual filter"
                      + " builder."));
      case "PROPOSAL" -> buildProposalResponse(proposal, book, categories, referenceDate);
      default ->
          throw new ApiException(
              HttpStatus.BAD_GATEWAY,
              "AI_FILTER_PROVIDER_ERROR",
              "The AI response could not be understood. Try again, or use the manual filter.");
    };
  }

  private FilterProposalResponse buildProposalResponse(
      ProviderProposal proposal,
      BookEntity book,
      List<CategoryEntity> categories,
      LocalDate referenceDate) {
    var ownedCategoryIds =
        categories.stream().map(CategoryEntity::getId).collect(Collectors.toSet());
    // Reuse the exact reference date the prompt told Gemini "today" was - recomputing it here
    // could disagree with the prompt across a midnight boundary in the book's timezone.
    Query query = mapper.buildValidatedQuery(proposal, book, ownedCategoryIds, referenceDate);

    Map<UUID, String> categoryNames =
        categories.stream()
            .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName));
    int digits = MoneyLimits.minorUnitDigits(book.getCurrencyCode());
    String summary =
        summaryFormatter.describe(query.filter(), categoryNames, digits, book.getCurrencyCode());

    return new FilterProposalResponse(
        "PROPOSAL",
        query.filter().canonical(),
        query.sortJson(),
        summary,
        null,
        safeMessage(proposal.limitation(), null));
  }

  private static String safeMessage(String raw, String fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    String trimmed = raw.strip();
    return trimmed.length() > MAX_MESSAGE_CHARS ? trimmed.substring(0, MAX_MESSAGE_CHARS) : trimmed;
  }

  private static ZoneId zoneIdFor(BookEntity book) {
    try {
      return ZoneId.of(book.getTimezone());
    } catch (DateTimeException | NullPointerException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid book timezone");
    }
  }

  private static int secondsUntilUtcMidnight() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime midnight =
        now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    return (int) Duration.between(now, midnight).toSeconds();
  }

  // package-private so FilterProposalServiceTest can check the prompt template's placeholders
  // all get filled without spinning up the full service (Gemini client, quota/rate limiters).
  String buildSystemInstruction(
      BookEntity book, List<CategoryEntity> categories, LocalDate referenceDate) {
    String fieldList =
        AiFilterFields.ALLOWED.stream()
            .sorted()
            .map(f -> "- " + f.wireName() + " (" + f.kind() + ")")
            .collect(Collectors.joining("\n"));
    String categoryList =
        categories.stream()
            .map(c -> "- " + c.getId() + " | " + c.getName() + " | " + c.getType())
            .collect(Collectors.joining("\n"));
    String operatorList =
        Arrays.stream(FilterOperator.values()).map(Enum::name).collect(Collectors.joining(", "));
    int digits = MoneyLimits.minorUnitDigits(book.getCurrencyCode());

    return SYSTEM_INSTRUCTION_TEMPLATE
        .replace("{{FIELD_LIST}}", fieldList)
        .replace("{{OPERATOR_LIST}}", operatorList)
        .replace("{{DATE_FIELD}}", TxField.OCCURRED_ON.wireName())
        .replace("{{CURRENCY}}", book.getCurrencyCode())
        .replace("{{DIGITS}}", String.valueOf(digits))
        .replace("{{TIMEZONE}}", book.getTimezone())
        .replace("{{TODAY}}", referenceDate.toString())
        .replace("{{CATEGORY_LIST}}", categoryList.isBlank() ? "(none)" : categoryList);
  }
}
