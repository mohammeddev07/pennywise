package com.axel.pennywise.api.dto.common;

import java.util.List;

public record ItemsResponse<T>(List<T> items) {}
