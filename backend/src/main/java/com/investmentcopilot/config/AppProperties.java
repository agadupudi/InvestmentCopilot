package com.investmentcopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        List<String> corsOrigins,
        int quoteCacheTtlSeconds
) {}
