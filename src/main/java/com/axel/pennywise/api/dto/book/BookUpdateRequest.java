package com.axel.pennywise.api.dto.book;

import jakarta.validation.constraints.Size;

public record BookUpdateRequest(@Size(max = 80) String name, String icon, String color) {
  public BookUpdateRequest(String name) {
    this(name, null, null);
  }
}
