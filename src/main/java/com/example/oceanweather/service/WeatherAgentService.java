package com.example.oceanweather.service;

import com.example.oceanweather.config.AgentProperties;
import com.example.oceanweather.model.WeatherRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class WeatherAgentService {

  private static final Logger log = LoggerFactory.getLogger(WeatherAgentService.class);
  private final OpenMeteoClient data;
  private final ChatClient chat;
  private final AgentProperties properties;
  private final JavaMailSender mail;
  private final CoastalSuggestionProvider coastalSuggestions;

  @Value("${spring.mail.username:}")
  private String from;

  @Autowired
  public WeatherAgentService(
      OpenMeteoClient data,
      ChatClient.Builder builder,
      AgentProperties properties,
      ObjectProvider<JavaMailSender> mail,
      CoastalSuggestionProvider coastalSuggestions) {
    this(data, builder.build(), properties, mail.getIfAvailable(), coastalSuggestions);
  }

  WeatherAgentService(
      OpenMeteoClient data,
      ChatClient chat,
      AgentProperties properties,
      JavaMailSender mail,
      CoastalSuggestionProvider coastalSuggestions) {
    this.data = data;
    this.chat = chat;
    this.properties = properties;
    this.mail = mail;
    this.coastalSuggestions = coastalSuggestions;
  }

  public Result createReport(WeatherRequest request) {
    log.info(
        "Weather request received: location={}, emailProvided={}",
        request.location(),
        request.email() != null);
    GeoLocation location =
        data.geocode(request.location(), 1).stream()
            .findFirst()
            .orElseThrow(() -> new LocationException("Location not found: " + request.location()));
    JsonNode marine = data.marine(location.latitude(), location.longitude());
    double seaLat = marine.path("latitude").asDouble(Double.NaN),
        seaLon = marine.path("longitude").asDouble(Double.NaN);
    double distance = distanceKm(location.latitude(), location.longitude(), seaLat, seaLon);
    log.info(
        "Coastal decision: location={}, seaGridDistanceKm={}, maxDistanceKm={}",
        location.displayName(),
        distance,
        properties.maxCoastalDistanceKm());
    if (!Double.isFinite(distance) || distance > properties.maxCoastalDistanceKm()) {
      List<String> suggestions = coastalSuggestions.nearestCoastalPlaces(location);
      throw new LocationException(
          location.displayName()
              + " is not within "
              + properties.maxCoastalDistanceKm()
              + " km of an ocean or sea. Nearest coastal suggestions: "
              + (suggestions.isEmpty()
                  ? "none found within the configured search radius"
                  : String.join("; ", suggestions)));
    }
    JsonNode weather = data.forecast(location.latitude(), location.longitude());
    String prompt =
        "Create a concise, factual coastal weather report. Use SI units by default and show"
            + " imperial equivalents in parentheses. Use only the supplied data, identify forecast"
            + " time zone, report any missing fields honestly, and include source names and URLs."
            + " Weather source: https://open-meteo.com/en/docs ; marine source:"
            + " https://open-meteo.com/en/docs/marine-weather-api\n"
            + "Location: "
            + location.displayName()
            + "\nDistance to nearest marine grid: "
            + Math.round(distance)
            + " km\nWeather API data: "
            + weather
            + "\nMarine API data: "
            + marine;
    log.info("AI prompt prepared: {}", prompt);
    String report = chat.prompt().user(prompt).call().content();
    log.info("Generated report for {}: {}", location.displayName(), report);
    String delivery = "stdout";
    if (request.email() != null && !request.email().isBlank()) {
      if (mail == null) {
        System.out.println(report);
        delivery = "stdout (email not configured)";
      } else {
        try {
          sendEmail(request.email(), location.displayName(), report);
          delivery = "email";
        } catch (DeliveryException ex) {
          System.out.println(report);
          delivery = "stdout (email failed)";
        }
      }
    } else System.out.println(report);
    return new Result(location.displayName(), report, delivery);
  }

  @Retryable(
      retryFor = Exception.class,
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public void sendEmail(String to, String location, String report) {
    SimpleMailMessage message = new SimpleMailMessage();
    if (from != null && !from.isBlank()) message.setFrom(from);
    message.setTo(to);
    message.setSubject("Coastal weather report: " + location);
    message.setText(report);
    mail.send(message);
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
