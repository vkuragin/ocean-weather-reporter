package com.example.oceanweather.api;

import com.example.oceanweather.model.WeatherRequest;
import com.example.oceanweather.model.WeatherTaskAccepted;
import com.example.oceanweather.model.WeatherTaskResponse;
import com.example.oceanweather.service.WeatherTaskManager;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

  private final WeatherTaskManager tasks;

  public WeatherController(WeatherTaskManager tasks) {
    this.tasks = tasks;
  }

  @PostMapping("/report")
  public ResponseEntity<WeatherTaskAccepted> report(@Valid @RequestBody WeatherRequest request) {
    return ResponseEntity.ok(tasks.submit(request));
  }

  @GetMapping("/tasks/{taskId}")
  public ResponseEntity<WeatherTaskResponse> status(@PathVariable UUID taskId) {
    return tasks
        .status(taskId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
