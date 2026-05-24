package com.investmentcopilot.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean JdbcTemplate jdbcTemplate;
    @MockBean StringRedisTemplate redisTemplate;
    @MockBean ValueOperations<String, String> valueOps;

    @Test
    void returns_ok_when_db_and_redis_healthy() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), (Class<Object>) any())).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.db").value(true))
                .andExpect(jsonPath("$.redis").value(true));
    }

    @Test
    void reports_db_false_when_query_throws() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), (Class<Object>) any()))
                .thenThrow(new RuntimeException("db down"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.db").value(false))
                .andExpect(jsonPath("$.redis").value(true));
    }

    @Test
    void reports_redis_false_when_ops_throw() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), (Class<Object>) any())).thenReturn(1);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.db").value(true))
                .andExpect(jsonPath("$.redis").value(false));
    }
}
