package com.example.oceanweather.service;

import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherTaskAccepted;
import com.example.oceanweather.model.WeatherTaskResponse;
import java.util.Optional;
import java.util.UUID;

public interface WeatherTaskManager {
  WeatherTaskAccepted submit(WeatherRequest request);

  Optional<WeatherTaskResponse> status(UUID taskId);
}
