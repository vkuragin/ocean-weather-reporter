package com.example.oceanweather.api;

import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherResponse;
import com.example.oceanweather.service.WeatherAgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

  private final WeatherAgentService agent;

  public WeatherController(WeatherAgentService agent) {
    this.agent = agent;
  }

  @PostMapping("/report")
  public ResponseEntity<WeatherResponse> report(@Valid @RequestBody WeatherRequest request) {
    WeatherAgentService.Result result = agent.createReport(request);
    return ResponseEntity.ok(
        new WeatherResponse(result.location(), result.report(), result.delivery()));
  }
}
