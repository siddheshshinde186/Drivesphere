package com.drivesphere.integration.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Client for Google Gemini API (model: gemini-2.5-flash-lite).
 * Adheres to Free-First principle: seamlessly falls back to domain-intelligent heuristic responses
 * when GEMINI_API_KEY is missing, ensuring the application starts and functions 100% offline.
 */
@Slf4j
@Component
public class GeminiClient {

    @Value("${drivesphere.gemini.api-key:${gemini.api.key:${GEMINI_API_KEY:}}}")
    private String apiKey;

    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("YOUR_GEMINI_API_KEY");
    }

    /**
     * Send prompt to Gemini API with fallback handling.
     */
    public String generateContent(String prompt, String fallbackResponse) {
        if (!isConfigured()) {
            log.info("Gemini API key not configured or set to placeholder. Operating in graceful fallback mode.");
            return fallbackResponse;
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[]{
                            Map.of("parts", new Object[]{
                                    Map.of("text", prompt)
                            })
                    }
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_ENDPOINT + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode textNode = candidates.get(0)
                            .path("content")
                            .path("parts")
                            .get(0)
                            .path("text");
                    if (!textNode.isMissingNode()) {
                        return textNode.asText();
                    }
                }
            } else {
                log.warn("Gemini API returned status code: {}. Response: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to call Gemini API due to: {}. Falling back gracefully.", e.getMessage());
        }

        return fallbackResponse;
    }
}
