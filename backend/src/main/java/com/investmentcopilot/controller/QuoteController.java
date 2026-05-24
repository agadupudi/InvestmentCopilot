package com.investmentcopilot.controller;

import com.investmentcopilot.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService service;

    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> getQuote(@PathVariable String symbol) {
        BigDecimal price = service.getPrice(symbol);
        if (price == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("symbol", symbol.toUpperCase(), "price", price));
    }

    @GetMapping
    public Map<String, BigDecimal> getQuotes(
            @RequestParam(required = false) List<String> symbols
    ) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        List<String> flat = symbols.stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .filter(s -> !s.isBlank())
                .toList();
        return service.getPrices(flat);
    }
}
