package dev.vk.oceanweather.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.vk.oceanweather.model.WeatherRequest;
import dev.vk.oceanweather.model.WeatherResponse;
import dev.vk.oceanweather.model.WeatherTaskAccepted;
import dev.vk.oceanweather.model.WeatherTaskResponse;
import dev.vk.oceanweather.model.WeatherTaskStatus;
import dev.vk.oceanweather.service.WeatherTaskManager;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class WeatherControllerTest {
  private static final UUID TASK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private LocalValidatorFactoryBean validator;
  private MockMvc mvc;
  private RecordingTaskManager tasks;

  @BeforeEach
  void setUp() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    tasks = new RecordingTaskManager();
    mvc =
        MockMvcBuilders.standaloneSetup(new WeatherController(tasks))
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @AfterEach
  void closeValidator() {
    validator.close();
  }

  @Test
  void createsTaskAndReturnsIdentifier() throws Exception {
    mvc.perform(
            post("/api/v1/weather/report")
                .contentType("application/json")
                .content(
                    """
                    {"location":"Lisbon","email":"reader@example.com"}
                    """
                        .trim()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskId").value(TASK_ID.toString()))
        .andExpect(jsonPath("$.statusUrl").value("/api/v1/weather/tasks/" + TASK_ID))
        .andExpect(jsonPath("$.status").value("PENDING"));

    assertEquals(new WeatherRequest("Lisbon", "reader@example.com"), tasks.request.get());
  }

  @Test
  void returnsTaskStatusAndResult() throws Exception {
    tasks.response =
        new WeatherTaskResponse(
            TASK_ID,
            WeatherTaskStatus.SUCCEEDED,
            new WeatherResponse("Lisbon, Portugal", "Sunny.", "stdout"),
            null);

    mvc.perform(get("/api/v1/weather/tasks/" + TASK_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskId").value(TASK_ID.toString()))
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.result.report").value("Sunny."))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void returnsNotFoundForUnknownTask() throws Exception {
    tasks.response = null;

    mvc.perform(get("/api/v1/weather/tasks/" + TASK_ID)).andExpect(status().isNotFound());
  }

  @Test
  void rejectsInvalidRequestWithoutCreatingTask() throws Exception {
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

    assertNull(tasks.request.get());
  }

  private static final class RecordingTaskManager implements WeatherTaskManager {
    private final AtomicReference<WeatherRequest> request = new AtomicReference<>();
    private WeatherTaskResponse response =
        new WeatherTaskResponse(TASK_ID, WeatherTaskStatus.PENDING, null, null);

    @Override
    public WeatherTaskAccepted submit(WeatherRequest request) {
      this.request.set(request);
      return new WeatherTaskAccepted(
          TASK_ID, "/api/v1/weather/tasks/" + TASK_ID, WeatherTaskStatus.PENDING);
    }

    @Override
    public Optional<WeatherTaskResponse> status(UUID taskId) {
      return Optional.ofNullable(response);
    }
  }
}
