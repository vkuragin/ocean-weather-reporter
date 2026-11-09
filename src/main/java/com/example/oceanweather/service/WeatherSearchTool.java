package com.example.oceanweather.service;

import com.example.oceanweather.config.AgentProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class WeatherSearchTool {

  private static final Logger log = LoggerFactory.getLogger(WeatherSearchTool.class);
  private static final String WEATHER_SOURCE = "https://open-meteo.com/en/docs";
  private static final String MARINE_SOURCE = "https://open-meteo.com/en/docs/marine-weather-api";

  private final WeatherDataClient weatherDataClient;
  private final AgentProperties agentProperties;
  private final CoastalSuggestionProvider coastalSuggestionProvider;

  public WeatherSearchTool(
      WeatherDataClient weatherDataClient,
      AgentProperties agentProperties,
      CoastalSuggestionProvider coastalSuggestionProvider) {
    this.weatherDataClient = weatherDataClient;
    this.agentProperties = agentProperties;
    this.coastalSuggestionProvider = coastalSuggestionProvider;
  }

  @Tool(
      name = "search_weather_and_ocean",
      description =
          "Resolve a location, verify that it is near an ocean or sea, and retrieve current"
              + " weather and marine conditions. Use this tool exactly once before writing a"
              + " report and use only its returned data.")
  public String search(
      @ToolParam(description = "The user's requested place name") String requestedLocation,
      ToolContext toolContext) {
    SearchState state = searchState(toolContext);
    try {
      SearchResult result = executeSearch(requestedLocation);
      state.succeed(result);
      return result.toolPayload();
    } catch (RuntimeException exception) {
      state.fail(exception);
      return "Weather search failed: " + safeMessage(exception);
    }
  }

  private SearchResult executeSearch(String requestedLocation) {
    GeoLocation location =
        weatherDataClient.geocode(requestedLocation, 1).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new WeatherAgentService.LocationException(
                        "Location not found: " + requestedLocation));
    JsonNode marine = weatherDataClient.marine(location.latitude(), location.longitude());
    double distance =
        distanceKm(
            location.latitude(),
            location.longitude(),
            marine.path("latitude").asDouble(Double.NaN),
            marine.path("longitude").asDouble(Double.NaN));
    log.info(
        "Coastal decision: location={}, seaGridDistanceKm={}, maxDistanceKm={}",
        location.displayName(),
        distance,
        agentProperties.maxCoastalDistanceKm());
    if (!Double.isFinite(distance) || distance > agentProperties.maxCoastalDistanceKm()) {
      List<String> suggestions = coastalSuggestionProvider.nearestCoastalPlaces(location);
      throw new WeatherAgentService.LocationException(
          location.displayName()
              + " is not within "
              + agentProperties.maxCoastalDistanceKm()
              + " km of an ocean or sea. Nearest coastal suggestions: "
              + (suggestions.isEmpty()
                  ? "none found within the configured search radius"
                  : String.join("; ", suggestions)));
    }
    JsonNode weather = weatherDataClient.forecast(location.latitude(), location.longitude());
    return new SearchResult(location, weather, marine, distance);
  }

  private static SearchState searchState(ToolContext toolContext) {
    Object value = toolContext.getContext().get("weatherSearchState");
    if (!(value instanceof SearchState state)) {
      throw new IllegalStateException("Weather search state is unavailable.");
    }
    return state;
  }

  private static String safeMessage(RuntimeException exception) {
    if (exception instanceof WeatherAgentService.LocationException) {
      return exception.getMessage();
    }
    return "The weather data provider is temporarily unavailable.";
  }

  private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
    double p1 = Math.toRadians(lat1),
        p2 = Math.toRadians(lat2),
        deltaLat = Math.toRadians(lat2 - lat1),
        deltaLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
    return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  public static final class SearchState {
    private volatile SearchResult result;
    private volatile RuntimeException failure;

    void succeed(SearchResult value) {
      result = value;
      failure = null;
    }

    void fail(RuntimeException exception) {
      failure = exception;
      result = null;
    }

    public SearchResult result() {
      return result;
    }

    public RuntimeException failure() {
      return failure;
    }
  }

  public record SearchResult(
      GeoLocation location, JsonNode weather, JsonNode marine, double distanceKm) {
    String toolPayload() {
      return "Resolved location: "
          + location.displayName()
          + "\nWeather coordinates (latitude, longitude): "
          + coordinatePair(weather, location.latitude(), location.longitude())
          + "\nMarine/ocean coordinates (latitude, longitude): "
          + coordinatePair(marine, location.latitude(), location.longitude())
          + "\nDistance to nearest marine grid: "
          + Math.round(distanceKm)
          + " km\nWeather source: "
          + WEATHER_SOURCE
          + "\nMarine source: "
          + MARINE_SOURCE
          + "\nWeather API data: "
          + weather
          + "\nMarine API data: "
          + marine;
    }

    private static String coordinatePair(
        JsonNode source, double fallbackLatitude, double fallbackLongitude) {
      return "("
          + source.path("latitude").asDouble(fallbackLatitude)
          + ", "
          + source.path("longitude").asDouble(fallbackLongitude)
          + ")";
    }
  }
}
