package dev.vk.oceanweather.service;

import java.util.List;

public interface CoastalSuggestionProvider {
  List<String> nearestCoastalPlaces(GeoLocation origin);
}
