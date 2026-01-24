package com.axel.pennywise.api.dto.export;

import java.util.UUID;

public record ExportCreateResponse(UUID exportId, String status) {}
