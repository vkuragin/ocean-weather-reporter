package dev.vk.oceanweather.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.util.StringUtils;

public class InMemoryWeatherReportCache implements WeatherReportCache {

  private final Clock clock;
  private final ConcurrentMap<CacheKey, CachedReport> reports = new ConcurrentHashMap<>();

  public InMemoryWeatherReportCache(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Optional<CachedReport> find(String requestedLocation) {
    return validLocation(requestedLocation)
        ? Optional.ofNullable(reports.get(key(requestedLocation)))
        : Optional.empty();
  }

  @Override
  public void store(String requestedLocation, String resolvedLocation, String report) {
    if (validLocation(requestedLocation)
        && StringUtils.hasText(resolvedLocation)
        && StringUtils.hasText(report)) {
      reports.put(key(requestedLocation), new CachedReport(resolvedLocation, report));
    }
  }

  private CacheKey key(String location) {
    return new CacheKey(normalize(location), LocalDate.now(clock));
  }

  private static boolean validLocation(String location) {
    return StringUtils.hasText(location);
  }

  private static String normalize(String location) {
    return location.trim().toLowerCase(Locale.ROOT);
  }

  private record CacheKey(String location, LocalDate date) {}
}
