package dev.vk.oceanweather.service;

import java.util.Optional;

public interface WeatherReportCache {

  Optional<CachedReport> find(String requestedLocation);

  void store(String requestedLocation, String resolvedLocation, String report);

  record CachedReport(String location, String report) {}
}
