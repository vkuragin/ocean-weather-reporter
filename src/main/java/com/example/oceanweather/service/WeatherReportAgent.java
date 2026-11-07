package com.example.oceanweather.service;

import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherResponse;

public interface WeatherReportAgent {
  WeatherResponse report(WeatherRequest request);
}
