package com.drivesphere.integration.weather;

import com.drivesphere.dto.TripPlannerResponse.WeatherSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WeatherClientTests {

    @SuppressWarnings("unchecked")
    private HttpResponse<String> createMockResponse(int statusCode, String body) {
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    @DisplayName("WeatherClient parses Open-Meteo response correctly")
    void getWeather_parsesResponseCorrectly() throws Exception {
        HttpClient mockHttp = Mockito.mock(HttpClient.class);
        String weatherJson = """
            {
              "current": {
                "temperature_2m": 24.5,
                "relative_humidity_2m": 65,
                "precipitation": 0.0,
                "weather_code": 1,
                "wind_speed_10m": 15.2
              }
            }
            """;
        Mockito.doReturn(createMockResponse(200, weatherJson))
                .when(mockHttp).send(any(HttpRequest.class), any());

        WeatherClient client = new WeatherClient(mockHttp);
        WeatherSummaryDto result = client.getWeather(12.9716, 77.5946);

        assertNotNull(result);
        assertEquals("Partly Cloudy", result.getCondition());
        assertNotNull(result.getTemperature());
        assertEquals(24.5, result.getTemperature(), 0.1);
    }

    @Test
    @DisplayName("WeatherClient returns graceful fallback weather on network timeout")
    void getWeather_onTimeout_returnsFallbackWeather() throws Exception {
        HttpClient mockHttp = Mockito.mock(HttpClient.class);
        Mockito.doThrow(new HttpConnectTimeoutException("Weather timeout"))
                .when(mockHttp).send(any(HttpRequest.class), any());

        WeatherClient client = new WeatherClient(mockHttp);
        WeatherSummaryDto result = client.getWeather(12.9716, 77.5946);

        assertNotNull(result);
        assertNotNull(result.getCondition());
        assertNotNull(result.getTemperature());
    }
}

