package com.investmentcopilot.dto;

import com.investmentcopilot.model.Holding;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HoldingReadDto(
        Integer id,
        String symbol,
        BigDecimal quantity,
        BigDecimal costBasis,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static HoldingReadDto from(Holding h) {
        return new HoldingReadDto(
                h.getId(), h.getSymbol(), h.getQuantity(),
                h.getCostBasis(), h.getNotes(), h.getCreatedAt(), h.getUpdatedAt()
        );
    }
}
