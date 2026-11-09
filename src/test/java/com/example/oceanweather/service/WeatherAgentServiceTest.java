package com.example.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.oceanweather.config.AgentProperties;
import com.example.oceanweather.model.WeatherRequest;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WeatherAgentServiceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String REPORT = "Coastal conditions are mild.";
  private final PrintStream originalOut = System.out;

  @AfterEach
  void restoreStandardOutput() {
    System.setOut(originalOut);
  }

  @Test
  void generatesReportAfterAIInvokesWeatherSearchTool() {
    AtomicReference<Prompt> prompt = new AtomicReference<>();
    AtomicInteger forecastCalls = new AtomicInteger();
    WeatherAgentService service =
        service(new StubWeatherData(true, forecastCalls), List.of(), null, "Lisbon", prompt);
    ByteArrayOutputStream output = captureOutput();

    WeatherAgentService.Result result = service.createReport(new WeatherRequest("Lisbon", null));

    assertEquals("Lisbon, Portugal", result.location());
    assertEquals(REPORT, result.report());
    assertEquals("stdout", result.delivery());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains(REPORT));
    assertEquals(1, forecastCalls.get());
    assertTrue(prompt.get().getContents().contains("search_weather_and_ocean"));
  }

  @Test
  void rejectsInlandLocationAndIncludesNearestCoastalSuggestions() {
    AtomicInteger forecastCalls = new AtomicInteger();
    WeatherAgentService service =
        service(
            new StubWeatherData(false, forecastCalls),
            List.of("Porto (4 km from shore)", "Aveiro (2 km from shore)"),
            null,
            "Madrid",
            new AtomicReference<>());

    WeatherAgentService.LocationException exception =
        assertThrows(
            WeatherAgentService.LocationException.class,
            () -> service.createReport(new WeatherRequest("Madrid", null)));

    assertTrue(exception.getMessage().contains("not within 50 km"));
    assertTrue(exception.getMessage().contains("Porto"));
    assertTrue(exception.getMessage().contains("Aveiro"));
    assertEquals(0, forecastCalls.get());
  }

  @Test
  void doesNotClaimEmailDeliveryWhenModelDoesNotInvokeEmailTool() {
    AtomicReference<SimpleMailMessage> sentMessage = new AtomicReference<>();
    WeatherAgentService service =
        service(
            new StubWeatherData(true, new AtomicInteger()),
            List.of(),
            new CapturingMailSender(sentMessage),
            "Lisbon",
            new AtomicReference<>());

    WeatherAgentService.Result result =
        service.createReport(new WeatherRequest("Lisbon", "reader@example.com"));

    assertEquals("email-not-sent", result.delivery());
    assertEquals(null, sentMessage.get());
  }

  @Test
  void printsReportWhenEmailIsAbsent() {
    WeatherAgentService service =
        service(
            new StubWeatherData(true, new AtomicInteger()),
            List.of(),
            null,
            "Lisbon",
            new AtomicReference<>());
    ByteArrayOutputStream output = captureOutput();

    WeatherAgentService.Result result = service.createReport(new WeatherRequest("Lisbon", ""));

    assertEquals("stdout", result.delivery());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains(REPORT));
  }

  private WeatherAgentService service(
      WeatherDataClient data,
      List<String> suggestions,
      JavaMailSender mail,
      String requestedLocation,
      AtomicReference<Prompt> prompt) {
    WeatherSearchTool searchTool =
        new WeatherSearchTool(
            data,
            new AgentProperties(50, 300, 5, new AgentProperties.Retry(1, 0, 1.0)),
            location -> suggestions);
    AtomicInteger modelCalls = new AtomicInteger();
    ChatModel model =
        new ChatModel() {
          @Override
          public ChatResponse call(Prompt request) {
            prompt.set(request);
            if (modelCalls.getAndIncrement() == 0) {
              ToolCallingChatOptions options =
                  (ToolCallingChatOptions) request.getOptions();
              ToolCallback searchCallback =
                  options.getToolCallbacks().stream()
                      .filter(
                          callback ->
                              callback
                                  .getToolDefinition()
                                  .name()
                                  .equals("search_weather_and_ocean"))
                      .findFirst()
                      .orElseThrow();
              searchCallback.call(
                  "{\"requestedLocation\":\"" + requestedLocation + "\"}",
                  new ToolContext(options.getToolContext()));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(REPORT))));
          }

          @Override
          public ToolCallingChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
          }
        };
    return new WeatherAgentService(
        ChatClient.builder(model).build(),
        searchTool,
        new EmailTool(new EmailDeliveryService(mail, "")));
  }

  private static ByteArrayOutputStream captureOutput() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    return output;
  }

  private static class StubWeatherData implements WeatherDataClient {
    private final boolean coastal;
    private final AtomicInteger forecastCalls;

    StubWeatherData(boolean coastal, AtomicInteger forecastCalls) {
      this.coastal = coastal;
      this.forecastCalls = forecastCalls;
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
      forecastCalls.incrementAndGet();
      return JSON.createObjectNode().put("temperature_2m", 18.0);
    }
  }

  private static class CapturingMailSender implements JavaMailSender {
    private final AtomicReference<SimpleMailMessage> sentMessage;

    CapturingMailSender(AtomicReference<SimpleMailMessage> sentMessage) {
      this.sentMessage = sentMessage;
    }

    @Override
    public void send(SimpleMailMessage message) {
      sentMessage.set(message);
    }

    @Override
    public void send(SimpleMailMessage... messages) {
      for (SimpleMailMessage message : messages) send(message);
    }

    @Override
    public jakarta.mail.internet.MimeMessage createMimeMessage() {
      throw new UnsupportedOperationException();
    }

    @Override
    public jakarta.mail.internet.MimeMessage createMimeMessage(java.io.InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void send(jakarta.mail.internet.MimeMessage... messages) {
      throw new UnsupportedOperationException();
    }
  }
}
