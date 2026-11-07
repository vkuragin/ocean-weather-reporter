package com.example.oceanweather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
    int maxCoastalDistanceKm, int suggestionRadiusKm, int suggestionCount, Retry retry) {

  public record Retry(int maxAttempts, long delayMs, double multiplier) {}
}
