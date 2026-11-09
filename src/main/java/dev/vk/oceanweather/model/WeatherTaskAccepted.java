package dev.vk.oceanweather.model;

import java.util.UUID;

public record WeatherTaskAccepted(UUID taskId, String statusUrl, WeatherTaskStatus status) {}
