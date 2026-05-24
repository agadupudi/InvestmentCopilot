package com.investmentcopilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.investmentcopilot.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuoteServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock RestTemplate restTemplate;
    @Mock AppProperties props;

    QuoteService service;

    @BeforeEach
    void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(props.quoteCacheTtlSeconds()).thenReturn(60);
        service = new QuoteService(redisTemplate, restTemplate, props);
    }

    @Test
    void getPrice_returns_cached_value_without_hitting_yahoo() {
        when(valueOps.get("quote:AAPL")).thenReturn("150.25");

        BigDecimal price = service.getPrice("aapl");

        assertThat(price).isEqualByComparingTo("150.25");
        verifyNoInteractions(restTemplate);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getPrice_fetches_from_yahoo_on_cache_miss_and_caches_result() {
        when(valueOps.get("quote:AAPL")).thenReturn(null);
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class), eq("AAPL")))
                .thenReturn(ResponseEntity.ok(yahooResponse(150.25)));

        BigDecimal price = service.getPrice("AAPL");

        assertThat(price).isEqualByComparingTo("150.25");
        verify(valueOps).set(eq("quote:AAPL"), eq("150.25"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void getPrice_returns_null_when_yahoo_throws() {
        when(valueOps.get("quote:AAPL")).thenReturn(null);
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class), eq("AAPL")))
                .thenThrow(new RestClientException("boom"));

        assertThat(service.getPrice("AAPL")).isNull();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getPrice_returns_null_when_yahoo_response_missing_price() {
        when(valueOps.get("quote:XYZ")).thenReturn(null);
        ObjectMapper m = new ObjectMapper();
        ObjectNode empty = m.createObjectNode();
        empty.putObject("chart").putArray("result");
        when(restTemplate.getForEntity(anyString(), eq(JsonNode.class), eq("XYZ")))
                .thenReturn(ResponseEntity.ok(empty));

        assertThat(service.getPrice("XYZ")).isNull();
    }

    @Test
    void getPrices_aggregates_results_with_uppercase_keys() {
        when(valueOps.get("quote:AAPL")).thenReturn("150");
        when(valueOps.get("quote:MSFT")).thenReturn("300");

        Map<String, BigDecimal> result = service.getPrices(List.of("aapl", "msft"));

        assertThat(result).containsOnlyKeys("AAPL", "MSFT");
        assertThat(result.get("AAPL")).isEqualByComparingTo("150");
        assertThat(result.get("MSFT")).isEqualByComparingTo("300");
    }

    private static JsonNode yahooResponse(double price) {
        ObjectMapper m = new ObjectMapper();
        ObjectNode root = m.createObjectNode();
        ObjectNode chart = root.putObject("chart");
        var arr = chart.putArray("result");
        ObjectNode result = arr.addObject();
        result.putObject("meta").put("regularMarketPrice", price);
        return root;
    }
}
