package com.axel.pennywise.api.dto.book;

import jakarta.validation.constraints.Size;

public record BookUpdateRequest(@Size(max = 80) String name) {}
