package com.axel.pennywise.domain.transaction.query.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axel.pennywise.domain.book.BookEntity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilterProposalServiceTest {

  private final FilterProposalService service =
      new FilterProposalService(null, null, null, null, null, null);

  private static BookEntity book() {
    BookEntity b = new BookEntity();
    b.setCurrencyCode("USD");
    b.setTimezone("UTC");
    return b;
  }

  @Test
  void systemInstructionTemplateHasNoUnfilledPlaceholders() {
    String instruction =
        service.buildSystemInstruction(book(), List.of(), LocalDate.of(2026, 6, 15));

    assertFalse(instruction.contains("{{"), "prompt template left an unfilled {{...}} placeholder");
    assertTrue(instruction.contains("2026-06-15"));
    assertTrue(instruction.contains("USD"));
  }
}
