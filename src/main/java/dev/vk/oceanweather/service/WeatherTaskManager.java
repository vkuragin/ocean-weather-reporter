package dev.vk.oceanweather.service;

import dev.vk.oceanweather.model.WeatherRequest;
import dev.vk.oceanweather.model.WeatherTaskAccepted;
import dev.vk.oceanweather.model.WeatherTaskResponse;
import java.util.Optional;
import java.util.UUID;

public interface WeatherTaskManager {
  WeatherTaskAccepted submit(WeatherRequest request);

  Optional<WeatherTaskResponse> status(UUID taskId);
}
