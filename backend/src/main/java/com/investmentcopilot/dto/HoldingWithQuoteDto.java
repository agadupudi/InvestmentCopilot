package com.investmentcopilot.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HoldingWithQuoteDto(
        Integer id,
        String symbol,
        BigDecimal quantity,
        BigDecimal costBasis,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        Double unrealizedPnlPct
) {}
