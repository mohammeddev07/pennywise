package com.axel.pennywise.api.dto.book;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BookOrderRequest(@NotNull List<@NotNull UUID> bookIds) {}
