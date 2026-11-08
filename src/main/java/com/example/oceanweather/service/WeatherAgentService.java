package com.example.oceanweather.service;

import com.example.oceanweather.config.AgentProperties;
import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeatherAgentService implements WeatherReportAgent {

  private static final Logger log = LoggerFactory.getLogger(WeatherAgentService.class);
  private final WeatherDataClient weatherDataClient;
  private final ChatClient openAiChatClient;
  private final AgentProperties agentProperties;
  private final JavaMailSender mailSender;
  private final CoastalSuggestionProvider coastalSuggestionProvider;

  @Value("${spring.mail.username:}")
  private String from;
  @Value("${spring.mail.enabled:true}")
  private boolean mailEnabled = true;

  public WeatherAgentService(
      WeatherDataClient weatherDataClient,
      ChatClient openAiChatClient,
      AgentProperties agentProperties,
      JavaMailSender mailSender,
      CoastalSuggestionProvider coastalSuggestionProvider) {
    this.weatherDataClient = weatherDataClient;
    this.openAiChatClient = openAiChatClient;
    this.agentProperties = agentProperties;
    this.mailSender = mailSender;
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
    log.info("AI prompt prepared: {}", prompt);
    String report = openAiChatClient.prompt().user(prompt).call().content();
    log.info("Generated report for {}: {}", location.displayName(), report);

    String delivery = "stdout";
    if (StringUtils.hasLength(request.email()) && mailEnabled) {
        try {
          sendEmail(request.email(), location.displayName(), report);
          delivery = "email";
        } catch (DeliveryException ex) {
          delivery = "email-failed";
          printToConsole("Failed to send email: " + ex.getMessage() + ". Printing report to console:\n" + report);
        }
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

  @Retryable(
      retryFor = Exception.class,
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public void sendEmail(String to, String location, String report) {
    if (mailSender == null) {
      log.info("Mail sender not configured, skipping...");
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    if (from != null && !from.isBlank()) message.setFrom(from);
    message.setTo(to);
    message.setSubject("Coastal weather report: " + location);
    message.setText(report);
    mailSender.send(message);
    log.info("Report email sent to {} for {}", to, location);
  }

  @Recover
  public void emailFailed(Exception ex, String to, String location, String report) {
    log.error(
        "Email delivery failed after configured retries for recipient={} location={}",
        to,
        location,
        ex);
    System.out.println(report);
    throw new DeliveryException(
        "Email delivery failed after retries; report was logged to application output.", ex);
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
