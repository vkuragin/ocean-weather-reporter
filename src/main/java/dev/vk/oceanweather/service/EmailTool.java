package dev.vk.oceanweather.service;

import dev.vk.oceanweather.service.WeatherAgentService.DeliveryException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class EmailTool {
  private final EmailDeliveryService deliveryService;

  public EmailTool(EmailDeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @Tool(
      name = "send_email",
      description =
          "Send the completed weather report to the email address explicitly supplied by the user."
              + " Call this only when an email address was supplied and after the report is"
              + " complete.")
  public String sendEmail(
      @ToolParam(description = "Resolved location name used in the report") String location,
      @ToolParam(description = "The complete weather report to send") String report,
      ToolContext toolContext) {
    DeliveryState state = deliveryState(toolContext);
    return sendReport(location, report, requestedEmail(toolContext), state);
  }

  public String sendReport(String location, String report, String recipient, DeliveryState state) {
    try {
      deliveryService.send(location, report, recipient);
      state.sent();
      return "Email sent successfully.";
    } catch (DeliveryException exception) {
      state.failed(exception.getMessage());
      return "Email delivery failed: " + exception.getMessage();
    }
  }

  private static String requestedEmail(ToolContext toolContext) {
    Object value = toolContext.getContext().get("requestedEmail");
    if (!(value instanceof String email) || email.isBlank()) {
      throw new DeliveryException("No explicitly requested email address is available.", null);
    }
    return email;
  }

  private static DeliveryState deliveryState(ToolContext toolContext) {
    Object value = toolContext.getContext().get("deliveryState");
    if (!(value instanceof DeliveryState state)) {
      throw new DeliveryException("Email delivery state is unavailable.", null);
    }
    return state;
  }

  public static final class DeliveryState {
    private volatile Status status = Status.NOT_ATTEMPTED;
    private volatile String error;

    public void sent() {
      status = Status.SENT;
    }

    public void failed(String message) {
      error = message;
      status = Status.FAILED;
    }

    public boolean sentSuccessfully() {
      return status == Status.SENT;
    }

    public String error() {
      return error;
    }

    private enum Status {
      NOT_ATTEMPTED,
      SENT,
      FAILED
    }
  }
}
