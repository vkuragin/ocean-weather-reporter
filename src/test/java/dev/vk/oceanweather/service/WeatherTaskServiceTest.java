package dev.vk.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.vk.oceanweather.model.WeatherRequest;
import dev.vk.oceanweather.model.WeatherResponse;
import dev.vk.oceanweather.model.WeatherTaskAccepted;
import dev.vk.oceanweather.model.WeatherTaskStatus;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

class WeatherTaskServiceTest {
  @Test
  void tracksTaskFromRunningToSucceeded() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    TaskExecutor executor = command -> new Thread(command).start();
    WeatherReportAgent agent =
        request -> {
          started.countDown();
          try {
            release.await(2, TimeUnit.SECONDS);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          return new WeatherResponse("Lisbon, Portugal", "Sunny.", "stdout");
        };
    WeatherTaskService tasks = new WeatherTaskService(agent, executor);

    WeatherTaskAccepted accepted = tasks.submit(new WeatherRequest("Lisbon", null));
    UUID taskId = accepted.taskId();

    started.await(2, TimeUnit.SECONDS);
    assertEquals(WeatherTaskStatus.RUNNING, tasks.status(taskId).orElseThrow().status());
    release.countDown();
    waitForStatus(tasks, taskId, WeatherTaskStatus.SUCCEEDED);
    assertEquals("Sunny.", tasks.status(taskId).orElseThrow().result().report());
  }

  @Test
  void recordsFailureWithoutExposingAnExceptionObject() throws Exception {
    TaskExecutor executor = Runnable::run;
    WeatherTaskService tasks =
        new WeatherTaskService(
            request -> {
              throw new IllegalStateException("weather provider unavailable");
            },
            executor);

    UUID taskId = tasks.submit(new WeatherRequest("Lisbon", null)).taskId();

    var response = tasks.status(taskId).orElseThrow();
    assertEquals(WeatherTaskStatus.FAILED, response.status());
    assertEquals("weather provider unavailable", response.error());
    assertNotNull(response.taskId());
  }

  private static void waitForStatus(
      WeatherTaskService tasks, UUID taskId, WeatherTaskStatus expected) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < deadline) {
      if (tasks.status(taskId).orElseThrow().status() == expected) return;
      Thread.sleep(10);
    }
    assertEquals(expected, tasks.status(taskId).orElseThrow().status());
  }
}
