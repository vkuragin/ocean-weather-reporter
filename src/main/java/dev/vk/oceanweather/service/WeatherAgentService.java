package dev.vk.oceanweather.service;

import dev.vk.oceanweather.model.WeatherRequest;
import dev.vk.oceanweather.model.WeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeatherAgentService implements WeatherReportAgent {

  private static final Logger log = LoggerFactory.getLogger(WeatherAgentService.class);
  private static final String PROMPT_TEMPLATE =
      """
      Create a concise, factual coastal weather markdown report including ocean hazards/warnings.
        Use SI units by default and
        show imperial equivalents in parentheses. Use only the supplied data, identify
        forecast time zone, report any missing fields honestly, and include source names
        and URLs. Include both source coordinate pairs in a Coordinates section.
        Requested location: %s
        Call the search_weather_and_ocean tool exactly once for the requested location
        before writing the report. Use only the tool's returned data and do not invent values.
        If the tool reports an error, do not write a report.
      """;
  private static final String PROMPT_TEMPLATE_EMAIL =
      """
      An email was explicitly requested. After preparing the complete report,
       call the send_email tool exactly once with the report and resolved location.
       Include the report in the output.
      """;

  private final WeatherReportGenerator reportGenerator;

  @Value("${spring.mail.enabled:true}")
  private boolean mailEnabled = true;

  public WeatherAgentService(WeatherReportGenerator reportGenerator) {
    this.reportGenerator = reportGenerator;
  }

  @Override
  public WeatherResponse report(WeatherRequest request) {
    Result result = createReport(request);
    return new WeatherResponse(result.location(), result.report(), result.delivery());
  }

  public Result createReport(WeatherRequest request) {
    log.info(
        "Weather request received: location={}, emailProvided={}",
        request.location(),
        request.email() != null);

    boolean emailRequested = mailEnabled && StringUtils.hasLength(request.email());
    String prompt = PROMPT_TEMPLATE.formatted(request.location());
    if (emailRequested) {
      prompt += PROMPT_TEMPLATE_EMAIL;
    }
    log.info("AI prompt prepared: {}", prompt);

    WeatherSearchTool.SearchState searchState = new WeatherSearchTool.SearchState();
    EmailTool.DeliveryState deliveryState = new EmailTool.DeliveryState();

    String report = reportGenerator.generate(prompt, searchState, deliveryState, request.email());

    if (searchState.failure() != null) {
      throw searchState.failure();
    }
    if (searchState.result() == null) {
      throw new LocationException("The AI did not invoke the weather search tool.");
    }
    String location = searchState.result().location().displayName();
    log.info("Generated report for {}: {}", location, report);

    String delivery = checkDeliveryState(emailRequested, deliveryState, report);
    return new Result(location, report, delivery);
  }

  private String checkDeliveryState(
      boolean emailRequested, EmailTool.DeliveryState deliveryState, String report) {
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
    return delivery;
  }

  private void printToConsole(String message) {
    System.out.println(message);
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
