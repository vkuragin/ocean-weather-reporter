package dev.vk.oceanweather.service;

public record GeoLocation(
    String name, String admin1, String country, double latitude, double longitude) {

  public String displayName() {
    return String.join(
        ", ",
        java.util.stream.Stream.of(name, admin1, country)
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .toList());
  }
}
