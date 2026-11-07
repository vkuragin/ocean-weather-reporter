package com.example.oceanweather.api;

import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherResponse;
import com.example.oceanweather.service.WeatherReportAgent;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

  private final WeatherReportAgent agent;

  public WeatherController(WeatherReportAgent agent) {
    this.agent = agent;
  }

  @PostMapping("/report")
  public ResponseEntity<WeatherResponse> report(@Valid @RequestBody WeatherRequest request) {
    return ResponseEntity.ok(agent.report(request));
  }
}
