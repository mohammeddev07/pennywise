package com.axel.pennywise.domain.book;

import com.axel.pennywise.exception.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Persisted contract keys. Theme colors belong to the client. */
public final class BookStyles {
  public static final String DEFAULT_ICON = "book";
  public static final String DEFAULT_COLOR = "green";
  public static final List<String> ICONS =
      List.of(
          "book", "briefcase", "airplane", "home", "car", "heart", "family", "education", "cart");
  public static final List<String> COLORS =
      List.of("green", "purple", "orange", "blue", "red", "pink");

  private BookStyles() {}

  public static void validate(String icon, String color) {
    if (icon != null && !ICONS.contains(icon)) invalid("icon");
    if (color != null && !COLORS.contains(color)) invalid("color");
  }

  private static void invalid(String field) {
    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid book " + field);
  }
}
