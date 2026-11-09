package com.example.oceanweather.service;

import com.example.oceanweather.config.AgentProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSearchToolTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void searchesWeatherAndMarineConditionsThroughTool() {
    WeatherSearchTool tool = tool(new StubWeatherData(true), List.of());
    WeatherSearchTool.SearchState state = new WeatherSearchTool.SearchState();

    String result = tool.search("Lisbon", new ToolContext(Map.of("weatherSearchState", state)));

    assertTrue(result.contains("Resolved location: Lisbon, Portugal"));
    assertTrue(result.contains("Weather coordinates (latitude, longitude): (38.7, -9.1)"));
    assertTrue(result.contains("Marine source: https://open-meteo.com/en/docs/marine-weather-api"));
    assertEquals("Lisbon, Portugal", state.result().location().displayName());
  }

  @Test
  void returnsNearestCoastalSuggestionsForInlandLocation() {
    WeatherSearchTool tool =
        tool(
            new StubWeatherData(false),
            List.of("Porto (4 km from shore)", "Aveiro (2 km from shore)"));
    WeatherSearchTool.SearchState state = new WeatherSearchTool.SearchState();

    String result = tool.search("Madrid", new ToolContext(Map.of("weatherSearchState", state)));

    assertTrue(result.contains("Porto"));
    assertTrue(result.contains("Aveiro"));
    assertInstanceOf(WeatherAgentService.LocationException.class, state.failure());
    assertNull(state.result());
  }

  private static WeatherSearchTool tool(WeatherDataClient data, List<String> suggestions) {
    return new WeatherSearchTool(
        data,
        new AgentProperties(50, 300, 5, new AgentProperties.Retry(1, 0, 1.0)),
        location -> suggestions);
  }

  private static final class StubWeatherData implements WeatherDataClient {
    private final boolean coastal;

    StubWeatherData(boolean coastal) {
      this.coastal = coastal;
    }

    @Override
    public List<GeoLocation> geocode(String query, int count) {
      return List.of(new GeoLocation(query, "", "Portugal", 38.7, -9.1));
    }

    @Override
    public JsonNode marine(double latitude, double longitude) {
      double marineLatitude = coastal ? latitude : latitude + 2;
      return JSON.createObjectNode().put("latitude", marineLatitude).put("longitude", longitude);
    }

    @Override
    public JsonNode forecast(double latitude, double longitude) {
      return JSON.createObjectNode().put("temperature_2m", 18.0);
    }
  }
}
