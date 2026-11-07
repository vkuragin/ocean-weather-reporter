package com.example.oceanweather.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WeatherRequestValidationTest {
  private static Validator validator;
  private static AutoCloseable validatorFactory;

  @BeforeAll
  static void createValidator() {
    var factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    validatorFactory = factory::close;
  }

  @AfterAll
  static void closeValidator() throws Exception {
    validatorFactory.close();
  }

  @Test
  void acceptsLocationWithoutEmail() {
    assertTrue(validator.validate(new WeatherRequest("Lisbon, Portugal", null)).isEmpty());
  }

  @Test
  void requiresNonBlankLocation() {
    var violations = validator.validate(new WeatherRequest("  ", null));

    assertEquals(1, violations.size());
    assertEquals("location", violations.iterator().next().getPropertyPath().toString());
  }

  @Test
  void validatesEmailWhenProvided() {
    var violations = validator.validate(new WeatherRequest("Lisbon, Portugal", "not-an-email"));

    assertEquals(1, violations.size());
    assertEquals("email", violations.iterator().next().getPropertyPath().toString());
  }
}
