package dev.vk.oceanweather.service;

import dev.vk.oceanweather.config.AgentProperties;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenStreetMapCoastalSuggestionProvider implements CoastalSuggestionProvider {
  private static final Logger log =
      LoggerFactory.getLogger(OpenStreetMapCoastalSuggestionProvider.class);
  private final WeatherDataClient data;
  private final AgentProperties properties;
  private final RestClient rest;

  public OpenStreetMapCoastalSuggestionProvider(
      WeatherDataClient data, AgentProperties properties, RestClient.Builder builder) {
    this.data = data;
    this.properties = properties;
    this.rest = builder.build();
  }

  @Override
  public List<String> nearestCoastalPlaces(GeoLocation origin) {
    String query =
        "[out:json][timeout:20];(node(around:"
            + (properties.suggestionRadiusKm() * 1000)
            + ","
            + origin.latitude()
            + ","
            + origin.longitude()
            + ")[place~\"city|town|village|hamlet\"];);out body 80;";
    JsonNode root = searchNearbyPlaces(query);
    List<Place> candidates = new ArrayList<>();
    if (root != null && root.has("elements")) {
      for (JsonNode element : root.get("elements")) {
        String name = element.path("tags").path("name").asText("");
        if (!name.isBlank() && element.has("lat") && element.has("lon")) {
          candidates.add(
              new Place(name, element.get("lat").asDouble(), element.get("lon").asDouble()));
        }
      }
    }
    return candidates.stream()
        .map(this::rank)
        .filter(place -> place.distance <= properties.maxCoastalDistanceKm())
        .sorted(Comparator.comparingDouble(RankedPlace::distance))
        .limit(properties.suggestionCount())
        .map(place -> place.name + " (" + Math.round(place.distance) + " km from shore)")
        .toList();
  }

  private JsonNode searchNearbyPlaces(String query) {
    for (int attempt = 1; attempt <= properties.retry().maxAttempts(); attempt++) {
      try {
        return rest.post()
            .uri("https://overpass-api.de/api/interpreter")
            .body(query)
            .retrieve()
            .body(JsonNode.class);
      } catch (RuntimeException exception) {
        if (attempt == properties.retry().maxAttempts()) {
          log.warn("Nearby-place search failed after {} attempts", attempt, exception);
          return null;
        }
        try {
          Thread.sleep(
              (long)
                  (properties.retry().delayMs()
                      * Math.pow(properties.retry().multiplier(), attempt - 1)));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return null;
        }
      }
    }
    return null;
  }

  private RankedPlace rank(Place place) {
    try {
      JsonNode marine = data.marine(place.latitude, place.longitude);
      double distance =
          distanceKm(
              place.latitude,
              place.longitude,
              marine.path("latitude").asDouble(Double.NaN),
              marine.path("longitude").asDouble(Double.NaN));
      return new RankedPlace(place.name, distance);
    } catch (RuntimeException exception) {
      log.warn("Could not evaluate coastal suggestion {}", place.name, exception);
      return new RankedPlace(place.name, Double.POSITIVE_INFINITY);
    }
  }

  private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double deltaLat = Math.toRadians(lat2 - lat1);
    double deltaLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
    return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private record Place(String name, double latitude, double longitude) {}

  private record RankedPlace(String name, double distance) {}
}
