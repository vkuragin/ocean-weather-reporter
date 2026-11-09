package dev.vk.oceanweather.service;

import dev.vk.oceanweather.model.WeatherRequest;
import dev.vk.oceanweather.model.WeatherResponse;
import dev.vk.oceanweather.model.WeatherTaskAccepted;
import dev.vk.oceanweather.model.WeatherTaskResponse;
import dev.vk.oceanweather.model.WeatherTaskStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class WeatherTaskService implements WeatherTaskManager {
  private static final Logger log = LoggerFactory.getLogger(WeatherTaskService.class);
  private final WeatherReportAgent agent;
  private final TaskExecutor executor;
  private final ConcurrentHashMap<UUID, TaskState> tasks = new ConcurrentHashMap<>();

  public WeatherTaskService(WeatherReportAgent agent, TaskExecutor executor) {
    this.agent = agent;
    this.executor = executor;
  }

  @Override
  public WeatherTaskAccepted submit(WeatherRequest request) {
    UUID taskId = UUID.randomUUID();
    TaskState state = new TaskState();
    tasks.put(taskId, state);
    try {
      executor.execute(() -> execute(taskId, request, state));
    } catch (RuntimeException exception) {
      state.fail(exception);
      log.error("Could not queue weather task {}", taskId, exception);
    }
    return new WeatherTaskAccepted(
        taskId, "/api/v1/weather/tasks/" + taskId, WeatherTaskStatus.PENDING);
  }

  @Override
  public Optional<WeatherTaskResponse> status(UUID taskId) {
    TaskState state = tasks.get(taskId);
    return state == null ? Optional.empty() : Optional.of(state.response(taskId));
  }

  private void execute(UUID taskId, WeatherRequest request, TaskState state) {
    state.start();
    try {
      state.succeed(agent.report(request));
      log.info("Weather task {} completed", taskId);
    } catch (Exception exception) {
      state.fail(exception);
      log.error("Weather task {} failed", taskId, exception);
    }
  }

  private static final class TaskState {
    private volatile WeatherTaskStatus status = WeatherTaskStatus.PENDING;
    private volatile WeatherResponse result;
    private volatile String error;

    void start() {
      status = WeatherTaskStatus.RUNNING;
    }

    void succeed(WeatherResponse value) {
      result = value;
      status = WeatherTaskStatus.SUCCEEDED;
    }

    void fail(Exception exception) {
      error =
          exception.getMessage() == null
              ? exception.getClass().getSimpleName()
              : exception.getMessage();
      status = WeatherTaskStatus.FAILED;
    }

    WeatherTaskResponse response(UUID taskId) {
      return new WeatherTaskResponse(taskId, status, result, error);
    }
  }
}
