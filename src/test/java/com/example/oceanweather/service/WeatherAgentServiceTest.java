package com.example.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.oceanweather.config.AgentProperties;
import com.example.oceanweather.model.WeatherRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class WeatherAgentServiceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String REPORT = "Coastal conditions are mild.";
  private final PrintStream originalOut = System.out;

  @AfterEach
  void restoreStandardOutput() {
    System.setOut(originalOut);
  }

  @Test
  void generatesReportForCoastalLocationAndPrintsItWhenEmailIsAbsent() throws Exception {
    AtomicReference<Prompt> prompt = new AtomicReference<>();
    AtomicInteger forecastCalls = new AtomicInteger();
    WeatherAgentService service =
        service(new StubWeatherData(true, forecastCalls), prompt, List.of(), null);
    ByteArrayOutputStream output = captureOutput();

    WeatherAgentService.Result result = service.createReport(new WeatherRequest("Lisbon", null));

    assertEquals("Lisbon, Portugal", result.location());
    assertEquals(REPORT, result.report());
    assertEquals("stdout", result.delivery());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains(REPORT));
    assertEquals(1, forecastCalls.get());
    assertTrue(prompt.get().getContents().contains("SI units by default"));
    assertTrue(prompt.get().getContents().contains("marine-weather-api"));
    assertTrue(
        prompt.get().getContents().contains("Weather data coordinates (latitude, longitude):"));
    assertTrue(
        prompt
            .get()
            .getContents()
            .contains("Marine/ocean data coordinates (latitude, longitude):"));
    assertTrue(prompt.get().getContents().contains("(38.7, -9.1)"));
  }

  @Test
  void rejectsInlandLocationAndIncludesNearestCoastalSuggestions() {
    AtomicInteger forecastCalls = new AtomicInteger();
    WeatherAgentService service =
        service(
            new StubWeatherData(false, forecastCalls),
            new AtomicReference<>(),
            List.of("Porto (4 km from shore)", "Aveiro (2 km from shore)"),
            null);

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
  void doesNotClaimEmailDeliveryWhenModelDoesNotInvokeTool() throws Exception {
    AtomicReference<SimpleMailMessage> sentMessage = new AtomicReference<>();
    JavaMailSender sender = new CapturingMailSender(sentMessage);
    WeatherAgentService service =
        service(
            new StubWeatherData(true, new AtomicInteger()),
            new AtomicReference<>(),
            List.of(),
            sender);

    WeatherAgentService.Result result =
        service.createReport(new WeatherRequest("Lisbon", "reader@example.com"));

    assertEquals("email-not-sent", result.delivery());
    assertEquals(null, sentMessage.get());
  }

  @Test
  void printsReportWhenEmailToolIsNotInvoked() throws Exception {
    WeatherAgentService service =
        service(
            new StubWeatherData(true, new AtomicInteger()),
            new AtomicReference<>(),
            List.of(),
            null);
    ByteArrayOutputStream output = captureOutput();

    WeatherAgentService.Result result = service.createReport(new WeatherRequest("Lisbon", ""));

    assertEquals("stdout", result.delivery());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains(REPORT));
  }

  private WeatherAgentService service(
      WeatherDataClient data,
      AtomicReference<Prompt> prompt,
      List<String> suggestions,
      JavaMailSender mail) {
    ChatModel model =
        request -> {
          prompt.set(request);
          return new ChatResponse(List.of(new Generation(new AssistantMessage(REPORT))));
        };
    return new WeatherAgentService(
        data,
        ChatClient.builder(model).build(),
        new AgentProperties(50, 300, 5, new AgentProperties.Retry(1, 0, 1.0)),
        new EmailTool(new EmailDeliveryService(mail, "")),
        location -> suggestions);
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
