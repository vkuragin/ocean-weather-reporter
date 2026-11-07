package com.example.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.oceanweather.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenStreetMapCoastalSuggestionProviderTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void returnsFiveNearestEligiblePlacesOrderedByDistance() throws Exception {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://overpass-api.de/api/interpreter"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("around:300000,0.0,0.0")))
        .andRespond(withSuccess(overpassResponse(), MediaType.APPLICATION_JSON));
    WeatherDataClient marineData =
        new WeatherDataClient() {
          @Override
          public List<GeoLocation> geocode(String query, int count) {
            throw new UnsupportedOperationException();
          }

          @Override
          public JsonNode forecast(double latitude, double longitude) {
            throw new UnsupportedOperationException();
          }

          @Override
          public JsonNode marine(double latitude, double longitude) {
            return JSON.createObjectNode().put("latitude", latitude).put("longitude", 0);
          }
        };
    var provider =
        new OpenStreetMapCoastalSuggestionProvider(
            marineData,
            new AgentProperties(50, 300, 5, new AgentProperties.Retry(1, 0, 1.0)),
            builder);

    List<String> suggestions =
        provider.nearestCoastalPlaces(new GeoLocation("Inland", "", "Country", 0, 0));

    assertEquals(
        List.of(
            "Place 1 (1 km from shore)",
            "Place 2 (2 km from shore)",
            "Place 3 (3 km from shore)",
            "Place 4 (4 km from shore)",
            "Place 5 (5 km from shore)"),
        suggestions);
    server.verify();
  }

  private static String overpassResponse() {
    return """
    {"elements":[
      {"lat":0,"lon":0.009,"tags":{"name":"Place 1"}},
      {"lat":0,"lon":0.018,"tags":{"name":"Place 2"}},
      {"lat":0,"lon":0.027,"tags":{"name":"Place 3"}},
      {"lat":0,"lon":0.036,"tags":{"name":"Place 4"}},
      {"lat":0,"lon":0.045,"tags":{"name":"Place 5"}},
      {"lat":0,"lon":0.054,"tags":{"name":"Place 6"}},
      {"lat":0,"lon":1,"tags":{"name":"Too Far"}}
    ]}
    """;
  }
}
