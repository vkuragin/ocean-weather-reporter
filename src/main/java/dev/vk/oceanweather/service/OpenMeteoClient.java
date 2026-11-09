package dev.vk.oceanweather.service;

import dev.vk.oceanweather.config.OpenMeteoProperties;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OpenMeteoClient implements WeatherDataClient {
  private final RestClient rest;
  private final OpenMeteoProperties properties;

  public OpenMeteoClient(RestClient rest, OpenMeteoProperties properties) {
    this.rest = rest;
    this.properties = properties;
  }

  @Override
  @Retryable(
      retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public List<GeoLocation> geocode(String query, int count) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Geocoding query must not be blank");
    }
    if (count < 1 || count > 100) {
      throw new IllegalArgumentException("Geocoding count must be between 1 and 100");
    }
    JsonNode root =
        get(
            UriComponentsBuilder.fromUriString(properties.geocodingUrl() + "/v1/search")
                .queryParam("name", query)
                .queryParam("count", count)
                .queryParam("language", "en")
                .queryParam("format", "json")
                .build()
                .toUri(),
            "geocoding");
    List<GeoLocation> result = new ArrayList<>();
    if (!root.has("results") || !root.get("results").isArray()) return result;
    for (JsonNode node : root.get("results")) {
      result.add(
          new GeoLocation(
              text(node, "name"),
              text(node, "admin1"),
              text(node, "country"),
              node.path("latitude").asDouble(),
              node.path("longitude").asDouble()));
    }
    return result;
  }

  @Override
  @Retryable(
      retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public JsonNode forecast(double latitude, double longitude) {
    validateCoordinates(latitude, longitude);
    return get(
        UriComponentsBuilder.fromUriString(properties.weatherUrl() + "/v1/forecast")
            .queryParam("latitude", latitude)
            .queryParam("longitude", longitude)
            .queryParam(
                "current",
                "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m")
            .queryParam(
                "daily",
                "temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max")
            .queryParam("temperature_unit", "celsius")
            .queryParam("wind_speed_unit", "kmh")
            .queryParam("forecast_days", properties.weatherForecastDays())
            .queryParam("timezone", "auto")
            .build()
            .toUri(),
        "weather forecast");
  }

  @Override
  @Retryable(
      retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public JsonNode marine(double latitude, double longitude) {
    validateCoordinates(latitude, longitude);
    return get(
        UriComponentsBuilder.fromUriString(properties.marineUrl() + "/v1/marine")
            .queryParam("latitude", latitude)
            .queryParam("longitude", longitude)
            .queryParam("hourly", "wave_height,wave_direction,wave_period,sea_surface_temperature")
            .queryParam("forecast_days", properties.marineForecastDays())
            .queryParam("cell_selection", properties.marineCellSelection())
            .build()
            .toUri(),
        "marine forecast");
  }

  private JsonNode get(java.net.URI uri, String source) {
    JsonNode body = rest.get().uri(uri).retrieve().body(JsonNode.class);
    if (body == null || body.isNull()) {
      throw new ExternalDataException("Empty response from Open-Meteo " + source);
    }
    return body;
  }

  private static void validateCoordinates(double latitude, double longitude) {
    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("Latitude or longitude is out of range");
    }
  }

  private static String text(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : "";
  }

  public static class ExternalDataException extends RuntimeException {
    public ExternalDataException(String message) {
      super(message);
    }
  }
}
