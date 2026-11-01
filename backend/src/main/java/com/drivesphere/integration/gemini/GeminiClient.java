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
 * Client for Google Gemini API (model: gemini-3.5-flash-lite).
 * Adheres to Free-First principle: seamlessly falls back to domain-intelligent heuristic responses
 * when GEMINI_API_KEY is missing, ensuring the application starts and functions 100% offline.
 */
@Slf4j
@Component
public class GeminiClient {

    @Value("${drivesphere.gemini.api-key:${gemini.api.key:${GEMINI_API_KEY:}}}")
    private String apiKey;

    @Value("${drivesphere.gemini.model:${gemini.model:${GEMINI_MODEL:gemini-3.5-flash-lite}}}")
    private String model = "gemini-3.5-flash-lite";

    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private HttpClient httpClient;

    private synchronized HttpClient getHttpClient() {
        if (this.httpClient == null) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return this.httpClient;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiClient() {}

    public GeminiClient(String apiKey, HttpClient httpClient) {
        this.apiKey = apiKey;
        if (httpClient != null) {
            this.httpClient = httpClient;
        }
    }

    public GeminiClient(String apiKey, String model, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.model = model;
        if (httpClient != null) {
            this.httpClient = httpClient;
        }
    }

    public void setHttpClient(HttpClient httpClient) {
        if (httpClient != null) {
            this.httpClient = httpClient;
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return (model != null && !model.isBlank()) ? model.trim() : "gemini-3.5-flash-lite";
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getModelDisplayName() {
        String m = getModel();
        if (m.equalsIgnoreCase("gemini-3.5-flash-lite")) {
            return "Google Gemini 3.5 Flash Lite";
        } else if (m.equalsIgnoreCase("gemini-2.5-flash-lite")) {
            return "Google Gemini 2.5 Flash Lite";
        }
        return "Google Gemini (" + m + ")";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("YOUR_GEMINI_API_KEY");
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class GeminiResult {
        private final boolean success;
        private final String text;
        private final String error;
        private final boolean configured;

        public static GeminiResult unconfigured() {
            return new GeminiResult(false, null, "API key not configured", false);
        }

        public static GeminiResult success(String text) {
            return new GeminiResult(true, text, null, true);
        }

        public static GeminiResult failure(String error) {
            return new GeminiResult(false, null, error, true);
        }
    }

    /**
     * Executes prompt against Gemini API and returns detailed execution status.
     */
    public GeminiResult executePrompt(String prompt) {
        if (!isConfigured()) {
            log.info("[GEMINI_AI] API key not configured or set to placeholder. Operating in graceful fallback mode.");
            return GeminiResult.unconfigured();
        }

        if (prompt == null || prompt.isBlank()) {
            return GeminiResult.failure("Prompt cannot be empty");
        }

        String effectiveModel = getModel();
        log.info("[GEMINI_AI] Dispatching prompt to Google Gemini API (model: {}, promptLength: {} chars)",
                effectiveModel, prompt.length());

        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[]{
                            Map.of("parts", new Object[]{
                                    Map.of("text", prompt)
                            })
                    }
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            String endpoint = GEMINI_BASE_URL + effectiveModel + ":generateContent?key=" + apiKey.trim();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode textNode = candidates.get(0)
                            .path("content")
                            .path("parts")
                            .get(0)
                            .path("text");
                    if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                        String generatedText = textNode.asText();
                        log.info("[GEMINI_AI] Successfully received response from Google Gemini API (model: {}, responseLength: {} chars)",
                                effectiveModel, generatedText.length());
                        return GeminiResult.success(generatedText);
                    }
                }
                log.warn("[GEMINI_AI] API returned 200 OK but response contained no valid candidate text: {}", response.body());
                return GeminiResult.failure("Empty candidate response from Gemini API");
            } else {
                log.warn("[GEMINI_AI] Request failed with HTTP {} for model {}. Response body: {}",
                        response.statusCode(), effectiveModel, response.body());
                return GeminiResult.failure("Gemini API returned HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("[GEMINI_AI] Exception calling Google Gemini API (model: {}): {}. Falling back gracefully.",
                    effectiveModel, e.getMessage());
            return GeminiResult.failure(e.getMessage());
        }
    }

    /**
     * Send prompt to Gemini API returning an Optional containing generated text if successful.
     */
    public java.util.Optional<String> generateContent(String prompt) {
        GeminiResult result = executePrompt(prompt);
        if (result.isSuccess() && result.getText() != null) {
            return java.util.Optional.of(result.getText());
        }
        return java.util.Optional.empty();
    }

    /**
     * Send prompt to Gemini API with fallback handling.
     */
    public String generateContent(String prompt, String fallbackResponse) {
        GeminiResult result = executePrompt(prompt);
        if (result.isSuccess() && result.getText() != null) {
            return result.getText();
        }
        return fallbackResponse;
    }
}
