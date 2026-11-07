package com.example.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.oceanweather.config.OpenMeteoProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class OpenMeteoClientTest {
  private RestClient.Builder builder;
  private MockRestServiceServer server;
  private OpenMeteoClient client;

  @BeforeEach
  void setUp() {
    builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new OpenMeteoClient(builder.build(), properties());
  }

  @AfterEach
  void verifyRequests() {
    server.verify();
  }

  @Test
  void geocodesLocationAndMapsResults() {
    server
        .expect(
            requestTo(
                "https://geocoding-api.open-meteo.com/v1/search?name=Lisbon,%20Portugal&count=2&language=en&format=json"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("count", "2"))
        .andRespond(
            withSuccess(
                """
                {"results":[{"name":"Lisbon","admin1":"Lisbon","country":"Portugal","latitude":38.72,"longitude":-9.14}]}
                """,
                MediaType.APPLICATION_JSON));

    List<GeoLocation> locations = client.geocode("Lisbon, Portugal", 2);

    assertEquals(1, locations.size());
    assertEquals("Lisbon, Portugal", locations.get(0).displayName());
    assertEquals(38.72, locations.get(0).latitude());
    assertEquals(-9.14, locations.get(0).longitude());
  }

  @Test
  void requestsMetricWeatherForecast() {
    server
        .expect(
            requestTo(org.hamcrest.Matchers.startsWith("https://api.open-meteo.com/v1/forecast?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("latitude", "38.7"))
        .andExpect(queryParam("timezone", "auto"))
        .andExpect(queryParam("forecast_days", "3"))
        .andExpect(queryParam("temperature_unit", "celsius"))
        .andExpect(queryParam("wind_speed_unit", "kmh"))
        .andRespond(
            withSuccess(
                """
                {"current":{"temperature_2m":18.2,"wind_speed_10m":12.0}}
                """,
                MediaType.APPLICATION_JSON));

    var forecast = client.forecast(38.7, -9.1);

    assertEquals(18.2, forecast.path("current").path("temperature_2m").asDouble());
    assertEquals(12.0, forecast.path("current").path("wind_speed_10m").asDouble());
  }

  @Test
  void requestsMarineWaveConditionsUsingSeaGridSelection() {
    server
        .expect(
            requestTo(
                org.hamcrest.Matchers.startsWith("https://marine-api.open-meteo.com/v1/marine?")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("cell_selection", "sea"))
        .andExpect(queryParam("forecast_days", "1"))
        .andExpect(
            queryParam("hourly", "wave_height,wave_direction,wave_period,sea_surface_temperature"))
        .andRespond(
            withSuccess(
                """
                {"latitude":38.7,"longitude":-9.1,"hourly":{"wave_height":[1.2]}}
                """,
                MediaType.APPLICATION_JSON));

    var marine = client.marine(38.7, -9.1);

    assertEquals(1.2, marine.path("hourly").path("wave_height").get(0).asDouble());
  }

  @Test
  void propagatesHttpErrorsToRetryLayer() {
    server
        .expect(
            requestTo(org.hamcrest.Matchers.startsWith("https://api.open-meteo.com/v1/forecast?")))
        .andRespond(withServerError());

    assertThrows(RestClientResponseException.class, () -> client.forecast(38.7, -9.1));
  }

  @Test
  void rejectsInvalidCoordinatesBeforeMakingRequest() {
    assertThrows(IllegalArgumentException.class, () -> client.forecast(91, 0));
  }

  @Test
  void rejectsBlankGeocodingQueryBeforeMakingRequest() {
    assertThrows(IllegalArgumentException.class, () -> client.geocode(" ", 1));
  }

  @Test
  void rejectsEmptyUpstreamResponse() {
    server
        .expect(
            requestTo(org.hamcrest.Matchers.startsWith("https://api.open-meteo.com/v1/forecast?")))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    assertThrows(OpenMeteoClient.ExternalDataException.class, () -> client.forecast(38.7, -9.1));
  }

  private static OpenMeteoProperties properties() {
    return new OpenMeteoProperties(
        "https://geocoding-api.open-meteo.com",
        "https://api.open-meteo.com",
        "https://marine-api.open-meteo.com",
        3,
        1,
        "sea",
        3000,
        10000);
  }
}
