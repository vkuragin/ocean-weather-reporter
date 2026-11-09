package com.example.oceanweather.service;

import com.example.oceanweather.config.AgentProperties;
import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherResponse;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeatherAgentService implements WeatherReportAgent {

  private static final Logger log = LoggerFactory.getLogger(WeatherAgentService.class);
  private final WeatherDataClient weatherDataClient;
  private final ChatClient openAiChatClient;
  private final AgentProperties agentProperties;
  private final EmailTool emailTool;
  private final CoastalSuggestionProvider coastalSuggestionProvider;

  @Value("${spring.mail.enabled:true}")
  private boolean mailEnabled = true;

  public WeatherAgentService(
      WeatherDataClient weatherDataClient,
      ChatClient openAiChatClient,
      AgentProperties agentProperties,
      EmailTool emailTool,
      CoastalSuggestionProvider coastalSuggestionProvider) {
    this.weatherDataClient = weatherDataClient;
    this.openAiChatClient = openAiChatClient;
    this.agentProperties = agentProperties;
    this.emailTool = emailTool;
    this.coastalSuggestionProvider = coastalSuggestionProvider;
  }

  public Result createReport(WeatherRequest request) {
    log.info(
        "Weather request received: location={}, emailProvided={}",
        request.location(),
        request.email() != null);
    GeoLocation location =
        weatherDataClient.geocode(request.location(), 1).stream()
            .findFirst()
            .orElseThrow(() -> new LocationException("Location not found: " + request.location()));
    JsonNode marine = weatherDataClient.marine(location.latitude(), location.longitude());
    double seaLat = marine.path("latitude").asDouble(Double.NaN),
        seaLon = marine.path("longitude").asDouble(Double.NaN);
    double distance = distanceKm(location.latitude(), location.longitude(), seaLat, seaLon);
    log.info(
        "Coastal decision: location={}, seaGridDistanceKm={}, maxDistanceKm={}",
        location.displayName(),
        distance,
        agentProperties.maxCoastalDistanceKm());
    if (!Double.isFinite(distance) || distance > agentProperties.maxCoastalDistanceKm()) {
      List<String> suggestions = coastalSuggestionProvider.nearestCoastalPlaces(location);
      throw new LocationException(
          location.displayName()
              + " is not within "
              + agentProperties.maxCoastalDistanceKm()
              + " km of an ocean or sea. Nearest coastal suggestions: "
              + (suggestions.isEmpty()
                  ? "none found within the configured search radius"
                  : String.join("; ", suggestions)));
    }
    JsonNode weather = weatherDataClient.forecast(location.latitude(), location.longitude());
    String weatherCoordinates = coordinatePair(weather, location.latitude(), location.longitude());
    String marineCoordinates = coordinatePair(marine, location.latitude(), location.longitude());
    boolean emailRequested = StringUtils.hasLength(request.email()) && mailEnabled;
    String prompt =
        "Create a concise, factual coastal weather markdown report. Use SI units by default and"
            + " show imperial equivalents in parentheses. Use only the supplied data, identify"
            + " forecast time zone, report any missing fields honestly, and include source names"
            + " and URLs. Include both source coordinate pairs in a Coordinates section. Weather"
            + " source: https://open-meteo.com/en/docs ; marine source:"
            + " https://open-meteo.com/en/docs/marine-weather-api\n"
            + "Location: "
            + location.displayName()
            + "\nWeather data coordinates (latitude, longitude): "
            + weatherCoordinates
            + "\nMarine/ocean data coordinates (latitude, longitude): "
            + marineCoordinates
            + "\nDistance to nearest marine grid: "
            + Math.round(distance)
            + " km\nWeather API data: "
            + weather
            + "\nMarine API data: "
            + marine;
    if (emailRequested) {
      prompt +=
          "\nAn email was explicitly requested. After preparing the complete report, call the"
              + " send_email tool exactly once with the report and resolved location.";
    }
    log.info("AI prompt prepared: {}", prompt);
    EmailTool.DeliveryState deliveryState = new EmailTool.DeliveryState();
    String report;
    if (emailRequested) {
      report =
          openAiChatClient
              .prompt()
              .user(prompt)
              .tools(emailTool)
              .toolContext(
                  Map.of("requestedEmail", request.email(), "deliveryState", deliveryState))
              .call()
              .content();
    } else {
      report = openAiChatClient.prompt().user(prompt).call().content();
    }
    log.info("Generated report for {}: {}", location.displayName(), report);

    String delivery = "stdout";
    if (emailRequested && deliveryState.sentSuccessfully()) {
      delivery = "email";
    } else if (emailRequested && deliveryState.error() != null) {
      delivery = "email-failed";
      printToConsole(
          "Failed to send email: "
              + deliveryState.error()
              + ". Printing report to console:\n"
              + report);
    } else if (emailRequested) {
      delivery = "email-not-sent";
      printToConsole(
          "The AI did not invoke the email tool. Printing report to console:\n" + report);
    }
    if (delivery.equals("stdout")) {
      printToConsole("Email not configured or disabled. Printing report to console:\n" + report);
    }
    return new Result(location.displayName(), report, delivery);
  }

  private void printToConsole(String message) {
    System.out.println(message);
  }

  private static String coordinatePair(
      JsonNode source, double fallbackLatitude, double fallbackLongitude) {
    return "("
        + source.path("latitude").asDouble(fallbackLatitude)
        + ", "
        + source.path("longitude").asDouble(fallbackLongitude)
        + ")";
  }

  @Override
  public WeatherResponse report(WeatherRequest request) {
    Result result = createReport(request);
    return new WeatherResponse(result.location(), result.report(), result.delivery());
  }

  private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
    double p1 = Math.toRadians(lat1),
        p2 = Math.toRadians(lat2),
        dp = Math.toRadians(lat2 - lat1),
        dl = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
    return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  public record Result(String location, String report, String delivery) {}

  public static class LocationException extends RuntimeException {
    public LocationException(String message) {
      super(message);
    }
  }

  public static class DeliveryException extends RuntimeException {
    public DeliveryException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
