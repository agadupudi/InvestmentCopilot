package com.investmentcopilot.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void teardown() {
        factory.close();
    }

    @Test
    void valid_create_dto_has_no_violations() {
        var dto = new HoldingCreateDto("AAPL", new BigDecimal("10"), new BigDecimal("150"), "ok");
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void blank_symbol_is_rejected() {
        var dto = new HoldingCreateDto("", new BigDecimal("10"), new BigDecimal("150"), null);
        Set<ConstraintViolation<HoldingCreateDto>> v = validator.validate(dto);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("symbol"));
    }

    @Test
    void oversize_symbol_is_rejected() {
        var dto = new HoldingCreateDto("A".repeat(17), new BigDecimal("10"), new BigDecimal("150"), null);
        assertThat(validator.validate(dto)).anyMatch(c -> c.getPropertyPath().toString().equals("symbol"));
    }

    @Test
    void zero_quantity_is_rejected() {
        var dto = new HoldingCreateDto("AAPL", BigDecimal.ZERO, new BigDecimal("150"), null);
        assertThat(validator.validate(dto)).anyMatch(c -> c.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    void negative_cost_basis_is_rejected() {
        var dto = new HoldingCreateDto("AAPL", new BigDecimal("10"), new BigDecimal("-1"), null);
        assertThat(validator.validate(dto)).anyMatch(c -> c.getPropertyPath().toString().equals("costBasis"));
    }

    @Test
    void null_quantity_is_rejected() {
        var dto = new HoldingCreateDto("AAPL", null, new BigDecimal("150"), null);
        assertThat(validator.validate(dto)).anyMatch(c -> c.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    void oversize_notes_is_rejected() {
        var dto = new HoldingCreateDto("AAPL", new BigDecimal("10"), new BigDecimal("150"), "x".repeat(513));
        assertThat(validator.validate(dto)).anyMatch(c -> c.getPropertyPath().toString().equals("notes"));
    }

    @Test
    void update_dto_allows_all_nulls() {
        var dto = new HoldingUpdateDto(null, null, null);
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void update_dto_rejects_negative_quantity() {
        var dto = new HoldingUpdateDto(new BigDecimal("-1"), null, null);
        assertThat(validator.validate(dto)).anyMatch(c -> c.getPropertyPath().toString().equals("quantity"));
    }
}
