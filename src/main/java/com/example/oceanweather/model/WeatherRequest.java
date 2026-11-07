package com.example.oceanweather.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record WeatherRequest(@NotBlank String location, @Email String email) {}
