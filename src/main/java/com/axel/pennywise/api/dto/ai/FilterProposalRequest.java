package com.axel.pennywise.api.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FilterProposalRequest(@NotBlank @Size(max = 500) String text) {}
