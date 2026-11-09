package dev.vk.oceanweather.service;

import dev.vk.oceanweather.model.WeatherRequest;
import dev.vk.oceanweather.model.WeatherResponse;

public interface WeatherReportAgent {
  WeatherResponse report(WeatherRequest request);
}
