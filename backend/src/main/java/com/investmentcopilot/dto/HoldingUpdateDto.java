package com.investmentcopilot.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record HoldingUpdateDto(
        @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @DecimalMin(value = "0", inclusive = false) BigDecimal costBasis,
        @Size(max = 512) String notes
) {}
