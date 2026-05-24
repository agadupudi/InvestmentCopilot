package com.investmentcopilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.investmentcopilot.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    private static final String CACHE_PREFIX = "quote:";
    private static final String YAHOO_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1d";

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final AppProperties props;

    public BigDecimal getPrice(String symbol) {
        symbol = symbol.toUpperCase().strip();
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + symbol);
        if (cached != null) {
            return new BigDecimal(cached);
        }
        BigDecimal price = fetchFromYahoo(symbol);
        if (price != null) {
            redisTemplate.opsForValue().set(
                    CACHE_PREFIX + symbol,
                    price.toPlainString(),
                    Duration.ofSeconds(props.quoteCacheTtlSeconds())
            );
        }
        return price;
    }

    public Map<String, BigDecimal> getPrices(List<String> symbols) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String symbol : symbols) {
            result.put(symbol.toUpperCase().strip(), getPrice(symbol));
        }
        return result;
    }

    private BigDecimal fetchFromYahoo(String symbol) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(YAHOO_URL, JsonNode.class, symbol);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode price = response.getBody()
                    .path("chart").path("result").path(0)
                    .path("meta").path("regularMarketPrice");
            if (price.isMissingNode() || price.isNull()) {
                return null;
            }
            return new BigDecimal(price.asText());
        } catch (Exception e) {
            log.warn("Failed to fetch quote for {}: {}", symbol, e.getMessage());
            return null;
        }
    }
}
