package com.example.oceanweather.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherResponse;
import com.example.oceanweather.service.WeatherAgentService.DeliveryException;
import com.example.oceanweather.service.WeatherAgentService.LocationException;
import com.example.oceanweather.service.WeatherReportAgent;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class WeatherControllerTest {
  private LocalValidatorFactoryBean validator;
  private MockMvc mvc;
  private RecordingAgent agent;

  @BeforeEach
  void setUp() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    agent = new RecordingAgent();
    mvc =
        MockMvcBuilders.standaloneSetup(new WeatherController(agent))
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @AfterEach
  void closeValidator() {
    validator.close();
  }

  @Test
  void forwardsValidRequestAndReturnsAgentResponse() throws Exception {
    mvc.perform(
            post("/api/v1/weather/report")
                .contentType("application/json")
                .content(
                    """
                    {"location":"Lisbon","email":"reader@example.com"}
                    """
                        .trim()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location").value("Lisbon, Portugal"))
        .andExpect(jsonPath("$.report").value("Test marine weather report."))
        .andExpect(jsonPath("$.delivery").value("email"));

    assertEquals(new WeatherRequest("Lisbon", "reader@example.com"), agent.request.get());
  }

  @Test
  void mapsLocationFailureToUnprocessableEntity() throws Exception {
    agent.failure = new LocationException("No coastal location found");

    mvc.perform(
            post("/api/v1/weather/report")
                .contentType("application/json")
                .content(
                    """
                    {"location":"Inland"}
                    """
                        .trim()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("No coastal location found"));
  }

  @Test
  void mapsDeliveryFailureToServiceUnavailable() throws Exception {
    agent.failure =
        new DeliveryException("Email delivery failed after retries", new RuntimeException());

    mvc.perform(
            post("/api/v1/weather/report")
                .contentType("application/json")
                .content(
                    """
                    {"location":"Lisbon","email":"reader@example.com"}
                    """
                        .trim()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("Email delivery failed after retries"));
  }

  @Test
  void rejectsInvalidRequestWithoutCallingAgent() throws Exception {
    mvc.perform(
            post("/api/v1/weather/report")
                .contentType("application/json")
                .content(
                    """
                    {"location":" ","email":"invalid"}
                    """
                        .trim()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertNull(agent.request.get());
  }

  private static final class RecordingAgent implements WeatherReportAgent {
    private final AtomicReference<WeatherRequest> request = new AtomicReference<>();
    private RuntimeException failure;

    @Override
    public WeatherResponse report(WeatherRequest request) {
      this.request.set(request);
      if (failure != null) {
        throw failure;
      }
      return new WeatherResponse("Lisbon, Portugal", "Test marine weather report.", "email");
    }
  }
}
