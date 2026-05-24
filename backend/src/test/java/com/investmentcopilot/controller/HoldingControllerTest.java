package com.investmentcopilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentcopilot.config.JacksonConfig;
import com.investmentcopilot.dto.HoldingCreateDto;
import com.investmentcopilot.dto.HoldingReadDto;
import com.investmentcopilot.dto.HoldingUpdateDto;
import com.investmentcopilot.dto.HoldingWithQuoteDto;
import com.investmentcopilot.service.HoldingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HoldingController.class)
@Import(JacksonConfig.class)
class HoldingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean HoldingService service;

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-01T12:00:00Z");

    @Test
    void list_returns_holdings_with_snake_case_keys_and_string_decimals() throws Exception {
        when(service.listWithQuotes()).thenReturn(List.of(new HoldingWithQuoteDto(
                1, "AAPL", new BigDecimal("10"), new BigDecimal("100"),
                "note", NOW, NOW,
                new BigDecimal("150"), new BigDecimal("1500.00"),
                new BigDecimal("500.00"), 50.0
        )));

        mockMvc.perform(get("/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].cost_basis").value("100"))
                .andExpect(jsonPath("$[0].last_price").value("150"))
                .andExpect(jsonPath("$[0].market_value").value("1500.00"))
                .andExpect(jsonPath("$[0].unrealized_pnl").value("500.00"))
                .andExpect(jsonPath("$[0].unrealized_pnl_pct").value(50.0));
    }

    @Test
    void create_returns_201_on_valid_payload() throws Exception {
        when(service.create(any())).thenReturn(new HoldingReadDto(
                1, "AAPL", new BigDecimal("10"), new BigDecimal("150"), null, NOW, NOW
        ));

        String body = """
                {"symbol":"aapl","quantity":"10","cost_basis":"150"}
                """;

        mockMvc.perform(post("/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void create_returns_400_when_validation_fails() throws Exception {
        String body = """
                {"symbol":"","quantity":"0","cost_basis":"-1"}
                """;

        mockMvc.perform(post("/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns_200_when_holding_exists() throws Exception {
        when(service.update(eq(1), any(HoldingUpdateDto.class)))
                .thenReturn(Optional.of(new HoldingReadDto(
                        1, "AAPL", new BigDecimal("20"), new BigDecimal("150"), "n", NOW, NOW
                )));

        String body = """
                {"quantity":"20"}
                """;

        mockMvc.perform(patch("/holdings/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value("20"));
    }

    @Test
    void update_returns_404_when_missing() throws Exception {
        when(service.update(eq(999), any(HoldingUpdateDto.class))).thenReturn(Optional.empty());

        mockMvc.perform(patch("/holdings/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns_204_when_present() throws Exception {
        when(service.delete(1)).thenReturn(true);

        mockMvc.perform(delete("/holdings/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns_404_when_missing() throws Exception {
        when(service.delete(999)).thenReturn(false);

        mockMvc.perform(delete("/holdings/999"))
                .andExpect(status().isNotFound());
    }
}
