package com.drivesphere.integration.gemini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class GeminiClientTests {

    @SuppressWarnings("unchecked")
    private HttpResponse<String> createMockResponse(int statusCode, String body) {
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    @DisplayName("Successful Gemini response parsing")
    void successfulResponseParsing() throws Exception {
        HttpClient mockHttp = Mockito.mock(HttpClient.class);
        String json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "The Tata Nexon EV is ideal for your trip."
                      }
                    ]
                  }
                }
              ]
            }
            """;
        Mockito.doReturn(createMockResponse(200, json))
                .when(mockHttp).send(any(HttpRequest.class), any());

        GeminiClient client = new GeminiClient("valid-test-key", mockHttp);

        GeminiClient.GeminiResult result = client.executePrompt("Recommend an EV");

        assertTrue(result.isSuccess());
        assertTrue(result.isConfigured());
        assertEquals("The Tata Nexon EV is ideal for your trip.", result.getText());

        Optional<String> content = client.generateContent("Recommend an EV");
        assertTrue(content.isPresent());
        assertEquals("The Tata Nexon EV is ideal for your trip.", content.get());
    }

    @Test
    @DisplayName("Missing or placeholder API key returns unconfigured result and fallback")
    void missingApiKey_shouldReturnUnconfiguredAndFallback() {
        GeminiClient client = new GeminiClient("", null);

        assertFalse(client.isConfigured());
        GeminiClient.GeminiResult result = client.executePrompt("Hello");
        assertFalse(result.isSuccess());
        assertFalse(result.isConfigured());

        String res = client.generateContent("Hello", "Local Fallback Answer");
        assertEquals("Local Fallback Answer", res);
    }

    @Test
    @DisplayName("HTTP 403 Forbidden returns failure and fallback")
    void httpError_shouldReturnFailureAndFallback() throws Exception {
        HttpClient mockHttp = Mockito.mock(HttpClient.class);
        Mockito.doReturn(createMockResponse(403, "{\"error\": \"API key invalid\"}"))
                .when(mockHttp).send(any(HttpRequest.class), any());

        GeminiClient client = new GeminiClient("fake-key", mockHttp);

        GeminiClient.GeminiResult result = client.executePrompt("Hello");
        assertFalse(result.isSuccess());
        assertTrue(result.isConfigured());
        assertTrue(result.getError().contains("403"));

        String res = client.generateContent("Hello", "Local Fallback Answer");
        assertEquals("Local Fallback Answer", res);
    }

    @Test
    @DisplayName("Network timeout returns failure and fallback")
    void timeoutException_shouldReturnFailureAndFallback() throws Exception {
        HttpClient mockHttp = Mockito.mock(HttpClient.class);
        Mockito.doThrow(new HttpConnectTimeoutException("Connection timed out"))
                .when(mockHttp).send(any(HttpRequest.class), any());

        GeminiClient client = new GeminiClient("fake-key", mockHttp);

        GeminiClient.GeminiResult result = client.executePrompt("Hello");
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Connection timed out"));

        String res = client.generateContent("Hello", "Local Fallback Answer");
        assertEquals("Local Fallback Answer", res);
    }

    @Test
    @DisplayName("Invalid JSON response without candidates returns failure")
    void invalidJsonResponse_shouldReturnFailure() throws Exception {
        HttpClient mockHttp = Mockito.mock(HttpClient.class);
        Mockito.doReturn(createMockResponse(200, "{\"candidates\": []}"))
                .when(mockHttp).send(any(HttpRequest.class), any());

        GeminiClient client = new GeminiClient("fake-key", mockHttp);

        GeminiClient.GeminiResult result = client.executePrompt("Hello");
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Empty candidate response"));
    }

    @Test
    @DisplayName("Empty or blank prompt rejected without network call")
    void emptyPrompt_shouldRejectFast() {
        GeminiClient client = new GeminiClient("fake-key", null);

        GeminiClient.GeminiResult result1 = client.executePrompt("");
        assertFalse(result1.isSuccess());
        assertEquals("Prompt cannot be empty", result1.getError());

        GeminiClient.GeminiResult result2 = client.executePrompt(null);
        assertFalse(result2.isSuccess());
        assertEquals("Prompt cannot be empty", result2.getError());
    }

    @Test
    @DisplayName("Model configuration, getters, setters, and display names")
    void modelConfiguration_shouldWorkProperly() {
        GeminiClient client = new GeminiClient();
        assertEquals("gemini-3.5-flash-lite", client.getModel());
        assertEquals("Google Gemini 3.5 Flash Lite", client.getModelDisplayName());

        client.setModel("gemini-1.5-pro");
        assertEquals("gemini-1.5-pro", client.getModel());
        assertEquals("Google Gemini (gemini-1.5-pro)", client.getModelDisplayName());

        client.setModel("gemini-2.5-flash-lite");
        assertEquals("Google Gemini 2.5 Flash Lite", client.getModelDisplayName());

        client.setModel("custom-model-x");
        assertEquals("Google Gemini (custom-model-x)", client.getModelDisplayName());

        client.setModel(null);
        assertEquals("gemini-3.5-flash-lite", client.getModel());

        client.setModel("   ");
        assertEquals("gemini-3.5-flash-lite", client.getModel());
    }
}
