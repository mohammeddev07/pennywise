package com.axel.pennywise.api.dto.summary;

import java.util.UUID;

public record CategoryBreakdownItem(UUID categoryId, long totalMinor) {}
