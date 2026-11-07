package com.example.oceanweather.api;

import com.example.oceanweather.service.WeatherAgentService.DeliveryException;
import com.example.oceanweather.service.WeatherAgentService.LocationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(LocationException.class)
  ResponseEntity<Map<String, String>> location(LocationException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "location is required; email must be valid when provided"));
  }

  @ExceptionHandler(DeliveryException.class)
  ResponseEntity<Map<String, String>> delivery(DeliveryException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("error", ex.getMessage()));
  }
}
