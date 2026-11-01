package com.drivesphere.integration.maps;

import com.drivesphere.dto.TripPlannerResponse.PoiDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MapsClientTests {

  @SuppressWarnings("unchecked")
  private HttpResponse<String> createMockResponse(int statusCode, String body) {
    HttpResponse<String> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(statusCode);
    when(response.body()).thenReturn(body);
    return response;
  }

  @Test
  @DisplayName("Geocode resolves verified static hub immediately without network call")
  void geocode_verifiedStaticHub_resolvesDirectly() {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    MapsClient client = new MapsClient(mockHttp);

    MapsClient.GeocodeResult result = client.geocode("Bangalore");
    assertNotNull(result);
    assertEquals(12.9716, result.getLat(), 0.0001);
    assertEquals(77.5946, result.getLng(), 0.0001);
    assertTrue(result.getDisplayName().contains("Bengaluru"));

    // Ensure no network calls were made for verified hubs
    Mockito.verifyNoInteractions(mockHttp);
  }

  @Test
  @DisplayName("Geocode resolves substring match for known mobility hubs")
  void geocode_substringMatch_resolvesDirectly() {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    MapsClient client = new MapsClient(mockHttp);

    MapsClient.GeocodeResult result = client.geocode("Pickup at Kempegowda Airport Terminal gate 2");
    assertNotNull(result);
    assertEquals(13.1986, result.getLat(), 0.0001);
    assertEquals(77.7066, result.getLng(), 0.0001);
    assertTrue(result.getDisplayName().contains("Airport"));

    Mockito.verifyNoInteractions(mockHttp);
  }

  @Test
  @DisplayName("Geocode queries Nominatim for unknown location and parses response")
  void geocode_queriesNominatimAndParsesResponse() throws Exception {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    String nominatimJson = """
        [
          {
            "lat": "13.0358",
            "lon": "77.5970",
            "display_name": "Hebbal, Bengaluru, Karnataka, India"
          }
        ]
        """;
    Mockito.doReturn(createMockResponse(200, nominatimJson))
        .when(mockHttp).send(any(HttpRequest.class), any());

    MapsClient client = new MapsClient(mockHttp);
    MapsClient.GeocodeResult result = client.geocode("Hebbal");

    assertNotNull(result);
    assertEquals(13.0358, result.getLat(), 0.0001);
    assertEquals(77.5970, result.getLng(), 0.0001);
    assertEquals("Hebbal, Bengaluru, Karnataka, India", result.getDisplayName());
  }

  @Test
  @DisplayName("Geocode throws clean IllegalArgumentException when location is unresolvable")
  void geocode_unresolvableLocation_throwsException() throws Exception {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    Mockito.doReturn(createMockResponse(200, "[]"))
        .when(mockHttp).send(any(HttpRequest.class), any());

    MapsClient client = new MapsClient(mockHttp);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
      client.geocode("NonExistentAtlantisHub9999");
    });

    assertTrue(ex.getMessage().contains("I couldn't locate 'NonExistentAtlantisHub9999'"));
  }

  @Test
  @DisplayName("Geocode rejects blank or null input")
  void geocode_blankQuery_throwsException() {
    MapsClient client = new MapsClient(Mockito.mock(HttpClient.class));

    assertThrows(IllegalArgumentException.class, () -> client.geocode(""));
    assertThrows(IllegalArgumentException.class, () -> client.geocode("   "));
    assertThrows(IllegalArgumentException.class, () -> client.geocode(null));
  }

  @Test
  @DisplayName("Calculate route via OpenRouteService when API key is configured")
  void calculateRoute_withOrsApiKey_usesOrs() throws Exception {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    String orsJson = """
        {
          "features": [
            {
              "properties": {
                "summary": {
                  "distance": 145000.0,
                  "duration": 9600.0
                }
              },
              "geometry": {
                "coordinates": [
                  [77.5946, 12.9716],
                  [76.6394, 12.2958]
                ]
              }
            }
          ]
        }
        """;
    Mockito.doReturn(createMockResponse(200, orsJson))
        .when(mockHttp).send(any(HttpRequest.class), any());

    MapsClient client = new MapsClient("valid-ors-key", mockHttp);
    assertTrue(client.isConfigured());

    MapsClient.RouteCalculationResult result = client.calculateRoute(12.9716, 77.5946, 12.2958, 76.6394);

    assertNotNull(result);
    assertEquals(new BigDecimal("145.00"), result.getDistanceKm());
    assertEquals(160, result.getDurationMinutes());
    assertEquals("OPENROUTESERVICE", result.getProvider());
    assertFalse(result.isFallback());
    assertEquals(2, result.getCoordinates().size());
  }

  @Test
  @DisplayName("Calculate route falls back to OSRM when ORS key is missing")
  void calculateRoute_withoutOrsKey_fallsBackToOsrm() throws Exception {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    String osrmJson = """
        {
          "routes": [
            {
              "distance": 150000.0,
              "duration": 10200.0,
              "geometry": {
                "coordinates": [
                  [77.5946, 12.9716],
                  [76.6394, 12.2958]
                ]
              }
            }
          ]
        }
        """;
    Mockito.doReturn(createMockResponse(200, osrmJson))
        .when(mockHttp).send(any(HttpRequest.class), any());

    MapsClient client = new MapsClient(null, mockHttp);
    assertFalse(client.isConfigured());

    MapsClient.RouteCalculationResult result = client.calculateRoute(12.9716, 77.5946, 12.2958, 76.6394);

    assertNotNull(result);
    assertEquals(new BigDecimal("150.00"), result.getDistanceKm());
    assertEquals(170, result.getDurationMinutes());
    assertEquals("OSRM", result.getProvider());
    assertFalse(result.isFallback());
  }

  @Test
  @DisplayName("Calculate route falls back to Haversine highway model when all HTTP calls fail")
  void calculateRoute_networkFailure_fallsBackToHaversine() throws Exception {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    Mockito.doThrow(new HttpConnectTimeoutException("Network down"))
        .when(mockHttp).send(any(HttpRequest.class), any());

    MapsClient client = new MapsClient("valid-key", mockHttp);

    MapsClient.RouteCalculationResult result = client.calculateRoute(12.9716, 77.5946, 12.2958, 76.6394);

    assertNotNull(result);
    assertTrue(result.isFallback());
    assertEquals("HAVERSINE_HIGHWAY_MODEL", result.getProvider());
    assertTrue(result.getDistanceKm().compareTo(BigDecimal.ZERO) > 0);
    assertTrue(result.getDurationMinutes() > 0);
    assertFalse(result.getCoordinates().isEmpty());
  }

  @Test
  @DisplayName("Nearby places parses Overpass API response correctly")
  void getNearbyPlaces_parsesElementsCorrectly() throws Exception {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    String overpassJson = """
        {
          "elements": [
            {
              "type": "node",
              "id": 101,
              "lat": 12.9720,
              "lon": 77.5950,
              "tags": {
                "name": "Shell Petrol Bunk",
                "amenity": "fuel",
                "brand": "Shell"
              }
            },
            {
              "type": "node",
              "id": 102,
              "lat": 12.9730,
              "lon": 77.5960,
              "tags": {
                "name": "Tata Power EV Fast Charger",
                "amenity": "charging_station"
              }
            }
          ]
        }
        """;
    Mockito.doReturn(createMockResponse(200, overpassJson))
        .when(mockHttp).send(any(HttpRequest.class), any());

    MapsClient client = new MapsClient(mockHttp);
    List<PoiDto> places = client.getNearbyPlaces(12.9716, 77.5946);

    assertEquals(2, places.size());
    assertEquals("Shell Petrol Bunk", places.get(0).getName());
    assertEquals("PETROL_PUMP", places.get(0).getCategory());
    assertEquals("Tata Power EV Fast Charger", places.get(1).getName());
    assertEquals("EV_CHARGER", places.get(1).getCategory());
  }

  @Test
  @DisplayName("Nearby places returns empty list gracefully on network error")
  void getNearbyPlaces_onException_returnsEmptyList() throws Exception {
    HttpClient mockHttp = Mockito.mock(HttpClient.class);
    Mockito.doThrow(new IOException("Timeout"))
        .when(mockHttp).send(any(HttpRequest.class), any());

    MapsClient client = new MapsClient(mockHttp);
    List<PoiDto> places = client.getNearbyPlaces(12.9716, 77.5946);

    assertNotNull(places);
    assertTrue(places.isEmpty());
  }
}
