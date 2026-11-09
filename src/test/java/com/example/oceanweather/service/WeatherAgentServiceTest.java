package com.example.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.oceanweather.model.WeatherRequest;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
  void generatesReportAfterWeatherSearchCompletes() {
    WeatherReportGenerator generator = generatorReturning(coastalResult());
    WeatherAgentService service = service(generator);
    ByteArrayOutputStream output = captureOutput();

    WeatherAgentService.Result result = service.createReport(new WeatherRequest("Lisbon", null));

    assertEquals("Lisbon, Portugal", result.location());
    assertEquals(REPORT, result.report());
    assertEquals("stdout", result.delivery());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains(REPORT));
    verify(generator).generate(anyString(), any(), any(), isNull());
  }

  @Test
  void includesRequestedLocationInAiPrompt() {
    WeatherReportGenerator generator = generatorReturning(coastalResult());
    WeatherAgentService service = service(generator);
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);

    service.createReport(new WeatherRequest("Lisbon", null));

    verify(generator).generate(prompt.capture(), any(), any(), isNull());
    assertTrue(prompt.getValue().contains("Requested location: Lisbon"));
  }

  @Test
  void propagatesWeatherSearchFailure() {
    WeatherAgentService.LocationException failure =
        new WeatherAgentService.LocationException("Location is not coastal.");
    WeatherReportGenerator generator = generatorFailing(failure);
    WeatherAgentService service = service(generator);

    WeatherAgentService.LocationException exception =
        assertThrows(
            WeatherAgentService.LocationException.class,
            () -> service.createReport(new WeatherRequest("Madrid", null)));

    assertSame(failure, exception);
  }

  @Test
  void rejectsReportWhenWeatherSearchWasNotInvoked() {
    WeatherReportGenerator generator = mock(WeatherReportGenerator.class);
    when(generator.generate(anyString(), any(), any(), any())).thenReturn(REPORT);
    WeatherAgentService service = service(generator);

    WeatherAgentService.LocationException exception =
        assertThrows(
            WeatherAgentService.LocationException.class,
            () -> service.createReport(new WeatherRequest("Lisbon", null)));

    assertEquals("The AI did not invoke the weather search tool.", exception.getMessage());
  }

  @Test
  void doesNotClaimEmailDeliveryWhenGeneratorDoesNotSendEmail() {
    WeatherAgentService service = service(generatorReturning(coastalResult()));

    WeatherAgentService.Result result =
        service.createReport(new WeatherRequest("Lisbon", "reader@example.com"));

    assertEquals("email-not-sent", result.delivery());
  }

  @Test
  void reportsSuccessfulEmailDeliveryWhenGeneratorSendsEmail() {
    WeatherReportGenerator generator = generatorSendingEmail(coastalResult());
    WeatherAgentService service = service(generator);

    WeatherAgentService.Result result =
        service.createReport(new WeatherRequest("Lisbon", "reader@example.com"));

    assertEquals("email", result.delivery());
    verify(generator).generate(anyString(), any(), any(), eq("reader@example.com"));
  }

  @Test
  void printsReportWhenEmailIsAbsent() {
    WeatherAgentService service = service(generatorReturning(coastalResult()));
    ByteArrayOutputStream output = captureOutput();

    WeatherAgentService.Result result = service.createReport(new WeatherRequest("Lisbon", ""));

    assertEquals("stdout", result.delivery());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains(REPORT));
  }

  private static WeatherAgentService service(WeatherReportGenerator generator) {
    return new WeatherAgentService(generator);
  }

  private static WeatherReportGenerator generatorReturning(
      WeatherSearchTool.SearchResult searchResult) {
    WeatherReportGenerator generator = mock(WeatherReportGenerator.class);
    when(generator.generate(anyString(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              WeatherSearchTool.SearchState state = invocation.getArgument(1);
              state.succeed(searchResult);
              return REPORT;
            });
    return generator;
  }

  private static WeatherReportGenerator generatorFailing(RuntimeException failure) {
    WeatherReportGenerator generator = mock(WeatherReportGenerator.class);
    when(generator.generate(anyString(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              WeatherSearchTool.SearchState state = invocation.getArgument(1);
              state.fail(failure);
              return REPORT;
            });
    return generator;
  }

  private static WeatherReportGenerator generatorSendingEmail(
      WeatherSearchTool.SearchResult searchResult) {
    WeatherReportGenerator generator = mock(WeatherReportGenerator.class);
    when(generator.generate(anyString(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              WeatherSearchTool.SearchState searchState = invocation.getArgument(1);
              EmailTool.DeliveryState deliveryState = invocation.getArgument(2);
              searchState.succeed(searchResult);
              deliveryState.sent();
              return REPORT;
            });
    return generator;
  }

  private static WeatherSearchTool.SearchResult coastalResult() {
    GeoLocation location = new GeoLocation("Lisbon", "", "Portugal", 38.7, -9.1);
    JsonNode weather = JSON.createObjectNode().put("temperature_2m", 18.0);
    JsonNode marine = JSON.createObjectNode().put("latitude", 38.7).put("longitude", -9.1);
    return new WeatherSearchTool.SearchResult(location, weather, marine, 0);
  }

  private static ByteArrayOutputStream captureOutput() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    return output;
  }
}
