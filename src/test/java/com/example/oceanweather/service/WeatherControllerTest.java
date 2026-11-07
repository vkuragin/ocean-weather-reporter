package com.example.oceanweather.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.oceanweather.api.ApiExceptionHandler;
import com.example.oceanweather.api.WeatherController;
import com.example.oceanweather.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class WeatherControllerTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private LocalValidatorFactoryBean validator;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(new WeatherController(service()))
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @AfterEach
  void closeValidator() {
    validator.close();
  }

  @Test
  void returnsReportForValidRequest() throws Exception {
    mvc.perform(
            post("/api/v1/weather/report")
                .contentType("application/json")
                .content(
                    """
                    {"location":"Lisbon"}
                    """
                        .trim()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location").value("Lisbon, Portugal"))
        .andExpect(jsonPath("$.report").value("Test marine weather report."))
        .andExpect(jsonPath("$.delivery").value("stdout"));
  }

  @Test
  void returnsUnprocessableEntityWithSuggestionsForInlandLocation() throws Exception {
    mvc.perform(
            post("/api/v1/weather/report")
                .contentType("application/json")
                .content(
                    """
                    {"location":"Inland"}
                    """
                        .trim()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Coastal City")));
  }

  @Test
  void rejectsInvalidRequestBeforeGeneratingReport() throws Exception {
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
  }

  private static WeatherAgentService service() {
    OpenMeteoClient data =
        new OpenMeteoClient() {
          @Override
          public List<GeoLocation> geocode(String query, int count) {
            if (query.equals("Inland")) {
              return List.of(new GeoLocation("Inland", "", "Country", 0, 0));
            }
            return List.of(new GeoLocation("Lisbon", "", "Portugal", 38.7, -9.1));
          }

          @Override
          public JsonNode marine(double latitude, double longitude) {
            double marineLatitude = latitude == 0 ? 2 : latitude;
            return JSON.createObjectNode()
                .put("latitude", marineLatitude)
                .put("longitude", longitude);
          }

          @Override
          public JsonNode forecast(double latitude, double longitude) {
            return JSON.createObjectNode().put("temperature_2m", 18.0);
          }
        };
    ChatModel model =
        prompt ->
            new ChatResponse(
                List.of(new Generation(new AssistantMessage("Test marine weather report."))));
    CoastalSuggestionProvider suggestions = location -> List.of("Coastal City (3 km from shore)");
    return new WeatherAgentService(
        data,
        ChatClient.builder(model).build(),
        new AgentProperties(50, 300, 5, new AgentProperties.Retry(1, 0, 1.0)),
        null,
        suggestions);
  }
}
