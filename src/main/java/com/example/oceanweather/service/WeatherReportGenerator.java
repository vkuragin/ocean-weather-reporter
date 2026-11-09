package com.example.oceanweather.service;

/** Generates a report through the configured AI client and registered tools. */
@FunctionalInterface
public interface WeatherReportGenerator {

  String generate(
      String prompt,
      WeatherSearchTool.SearchState searchState,
      EmailTool.DeliveryState deliveryState,
      String requestedEmail);
}
