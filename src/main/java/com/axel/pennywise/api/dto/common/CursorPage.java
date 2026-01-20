package com.axel.pennywise.api.dto.common;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor) {}
