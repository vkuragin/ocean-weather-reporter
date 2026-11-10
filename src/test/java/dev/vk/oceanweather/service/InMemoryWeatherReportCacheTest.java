package dev.vk.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class InMemoryWeatherReportCacheTest {

  @Test
  void normalizesLocationWhenReadingAndWriting() {
    MutableClock clock = new MutableClock(Instant.parse("2026-11-09T12:00:00Z"));
    InMemoryWeatherReportCache cache = new InMemoryWeatherReportCache(clock);

    cache.store(" Lisbon ", "Lisbon, Portugal", "Sunny.");

    assertEquals(
        new WeatherReportCache.CachedReport("Lisbon, Portugal", "Sunny."),
        cache.find("lisbon").orElseThrow());
  }

  @Test
  void doesNotReuseReportFromAnotherDate() {
    MutableClock clock = new MutableClock(Instant.parse("2026-11-09T12:00:00Z"));
    InMemoryWeatherReportCache cache = new InMemoryWeatherReportCache(clock);
    cache.store("Lisbon", "Lisbon, Portugal", "Sunny.");

    clock.advance(Duration.ofDays(1));

    assertTrue(cache.find("Lisbon").isEmpty());
  }

  @Test
  void ignoresIncompleteEntries() {
    InMemoryWeatherReportCache cache =
        new InMemoryWeatherReportCache(
            Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneId.of("UTC")));

    cache.store("", "Lisbon, Portugal", "Sunny.");
    cache.store("Lisbon", "", "Sunny.");
    cache.store("Lisbon", "Lisbon, Portugal", "");

    assertTrue(cache.find("Lisbon").isEmpty());
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(instant);
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
