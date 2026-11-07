package com.example.oceanweather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class OceanWeatherApplication {

  public static void main(String[] args) {
    SpringApplication.run(OceanWeatherApplication.class, args);
  }
}
