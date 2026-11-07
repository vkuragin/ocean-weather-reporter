package com.example.oceanweather.model;

import java.util.UUID;

public record WeatherTaskResponse(
    UUID taskId, WeatherTaskStatus status, WeatherResponse result, String error) {}
