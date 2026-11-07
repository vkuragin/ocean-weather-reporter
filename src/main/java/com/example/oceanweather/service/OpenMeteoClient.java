package com.example.oceanweather.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OpenMeteoClient {

  private final RestClient rest = RestClient.create();

  @Retryable(
      retryFor = Exception.class,
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public List<GeoLocation> geocode(String query, int count) {
    JsonNode root =
        rest.get()
            .uri(
                UriComponentsBuilder.fromUriString("https://geocoding-api.open-meteo.com/v1/search")
                    .queryParam("name", query)
                    .queryParam("count", count)
                    .queryParam("language", "en")
                    .queryParam("format", "json")
                    .build()
                    .toUri())
            .retrieve()
            .body(JsonNode.class);
    List<GeoLocation> result = new ArrayList<>();
    if (root == null || !root.has("results")) return result;
    for (JsonNode n : root.get("results"))
      result.add(
          new GeoLocation(
              text(n, "name"),
              text(n, "admin1"),
              text(n, "country"),
              n.path("latitude").asDouble(),
              n.path("longitude").asDouble()));
    return result;
  }

  @Retryable(
      retryFor = Exception.class,
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public JsonNode forecast(double lat, double lon) {
    return rest.get()
        .uri(
            UriComponentsBuilder.fromUriString("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam(
                    "current",
                    "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m")
                .queryParam(
                    "daily",
                    "temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max")
                .queryParam("forecast_days", 3)
                .queryParam("timezone", "auto")
                .build()
                .toUri())
        .retrieve()
        .body(JsonNode.class);
  }

  @Retryable(
      retryFor = Exception.class,
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public JsonNode marine(double lat, double lon) {
    return rest.get()
        .uri(
            UriComponentsBuilder.fromUriString("https://marine-api.open-meteo.com/v1/marine")
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam(
                    "hourly", "wave_height,wave_direction,wave_period,sea_surface_temperature")
                .queryParam("forecast_days", 1)
                .queryParam("cell_selection", "sea")
                .build()
                .toUri())
        .retrieve()
        .body(JsonNode.class);
  }

  private String text(JsonNode n, String field) {
    return n.hasNonNull(field) ? n.get(field).asText() : "";
  }
}
