package com.investmentcopilot.dto;

import com.investmentcopilot.model.Holding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingReadDtoTest {

    @Test
    void from_maps_all_fields() {
        OffsetDateTime created = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        OffsetDateTime updated = OffsetDateTime.parse("2026-02-01T11:00:00Z");

        Holding h = new Holding();
        h.setId(42);
        h.setSymbol("AAPL");
        h.setQuantity(new BigDecimal("10.5"));
        h.setCostBasis(new BigDecimal("150.25"));
        h.setNotes("note");
        h.setCreatedAt(created);
        h.setUpdatedAt(updated);

        HoldingReadDto dto = HoldingReadDto.from(h);

        assertThat(dto.id()).isEqualTo(42);
        assertThat(dto.symbol()).isEqualTo("AAPL");
        assertThat(dto.quantity()).isEqualByComparingTo("10.5");
        assertThat(dto.costBasis()).isEqualByComparingTo("150.25");
        assertThat(dto.notes()).isEqualTo("note");
        assertThat(dto.createdAt()).isEqualTo(created);
        assertThat(dto.updatedAt()).isEqualTo(updated);
    }

    @Test
    void from_preserves_null_notes() {
        Holding h = new Holding();
        h.setId(1);
        h.setSymbol("X");
        h.setQuantity(BigDecimal.ONE);
        h.setCostBasis(BigDecimal.ONE);
        h.setCreatedAt(OffsetDateTime.now());
        h.setUpdatedAt(OffsetDateTime.now());

        HoldingReadDto dto = HoldingReadDto.from(h);
        assertThat(dto.notes()).isNull();
    }
}
