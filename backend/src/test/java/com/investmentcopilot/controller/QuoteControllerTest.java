package com.investmentcopilot.controller;

import com.investmentcopilot.config.JacksonConfig;
import com.investmentcopilot.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuoteController.class)
@Import(JacksonConfig.class)
class QuoteControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean QuoteService service;

    @Test
    void single_quote_returns_200_with_price() throws Exception {
        when(service.getPrice("AAPL")).thenReturn(new BigDecimal("150.25"));

        mockMvc.perform(get("/quotes/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.price").value("150.25"));
    }

    @Test
    void single_quote_returns_404_when_price_null() throws Exception {
        when(service.getPrice("XYZ")).thenReturn(null);

        mockMvc.perform(get("/quotes/XYZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void batch_quote_empty_when_no_symbols() throws Exception {
        mockMvc.perform(get("/quotes"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    void batch_quote_splits_comma_separated_symbols() throws Exception {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        prices.put("AAPL", new BigDecimal("150"));
        prices.put("MSFT", new BigDecimal("300"));
        when(service.getPrices(List.of("AAPL", "MSFT"))).thenReturn(prices);

        mockMvc.perform(get("/quotes").param("symbols", "AAPL,MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AAPL").value("150"))
                .andExpect(jsonPath("$.MSFT").value("300"));
    }
}
