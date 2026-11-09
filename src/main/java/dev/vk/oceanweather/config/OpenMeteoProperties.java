package dev.vk.oceanweather.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "open-meteo")
@Validated
public record OpenMeteoProperties(
    @NotBlank String geocodingUrl,
    @NotBlank String weatherUrl,
    @NotBlank String marineUrl,
    @Positive int weatherForecastDays,
    @Positive int marineForecastDays,
    @NotBlank String marineCellSelection,
    @Positive int connectTimeoutMs,
    @Positive int readTimeoutMs) {}
