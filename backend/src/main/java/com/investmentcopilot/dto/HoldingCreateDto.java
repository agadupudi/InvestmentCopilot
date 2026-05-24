package com.investmentcopilot.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record HoldingCreateDto(
        @NotBlank @Size(max = 16) String symbol,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal costBasis,
        @Size(max = 512) String notes
) {}
