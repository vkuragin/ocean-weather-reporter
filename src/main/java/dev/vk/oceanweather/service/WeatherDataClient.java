package dev.vk.oceanweather.service;

import tools.jackson.databind.JsonNode;
import java.util.List;

public interface WeatherDataClient {
  List<GeoLocation> geocode(String query, int count);

  JsonNode forecast(double latitude, double longitude);

  JsonNode marine(double latitude, double longitude);
}
