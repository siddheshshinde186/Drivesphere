package com.drivesphere.integration.cloudinary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cloudinary Media Asset Integration.
 * Performs authenticated direct multipart/base64 uploads to Cloudinary API
 * when credentials are provided, or seamlessly falls back to secure local storage.
 */
@Slf4j
@Component
public class CloudinaryClient {

    @Value("${drivesphere.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${drivesphere.cloudinary.api-key:}")
    private String apiKey;

    @Value("${drivesphere.cloudinary.api-secret:}")
    private String apiSecret;

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

    public boolean isConfigured() {
        return cloudName != null && !cloudName.isBlank() 
                && !cloudName.equalsIgnoreCase("YOUR_CLOUDINARY_CLOUD_NAME")
                && !cloudName.contains("your_cloudinary")
                && apiKey != null && !apiKey.isBlank() 
                && !apiKey.equalsIgnoreCase("YOUR_CLOUDINARY_API_KEY")
                && !apiKey.contains("your_cloudinary")
                && apiSecret != null && !apiSecret.isBlank()
                && !apiSecret.contains("your_cloudinary");
    }

    /**
     * Performs authenticated file upload to Cloudinary and returns secure URL.
     */
    public String uploadBytes(byte[] fileBytes, String originalFilename, String contentType) {
        return uploadBytes(fileBytes, originalFilename, contentType, false);
    }

    /**
     * Performs file upload to Cloudinary.
     * When isPrivate is true, assets are marked with authenticated access mode
     * preventing unrestricted public CDN access.
     */
    public String uploadBytes(byte[] fileBytes, String originalFilename, String contentType, boolean isPrivate) {
        if (!isConfigured()) {
            log.info("Cloudinary credentials not set. Falling back to local secure object storage.");
            return null;
        }

        try {
            long timestamp = Instant.now().getEpochSecond();
            String stringToSign = (isPrivate ? "access_mode=authenticated&" : "") + "timestamp=" + timestamp + apiSecret;
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] signatureBytes = sha1.digest(stringToSign.getBytes(StandardCharsets.UTF_8));
            String signature = HexFormat.of().formatHex(signatureBytes);

            String base64Data = "data:" + (contentType != null ? contentType : "application/octet-stream")
                    + ";base64," + Base64.getEncoder().encodeToString(fileBytes);

            StringBuilder formBodyBuilder = new StringBuilder();
            formBodyBuilder.append("file=").append(URLEncoder.encode(base64Data, StandardCharsets.UTF_8))
                    .append("&api_key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8))
                    .append("&timestamp=").append(timestamp);
            if (isPrivate) {
                formBodyBuilder.append("&access_mode=authenticated");
            }
            formBodyBuilder.append("&signature=").append(signature);
            String formBody = formBodyBuilder.toString();

            String endpoint = "https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = objectMapper.readTree(response.body());
                String secureUrl = json.path("secure_url").asText(null);
                if (secureUrl != null && !secureUrl.isBlank()) {
                    log.info("Successfully uploaded file {} to Cloudinary: {}", originalFilename, secureUrl);
                    return secureUrl;
                }
            } else {
                log.warn("Cloudinary upload returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error uploading to Cloudinary: {}", e.getMessage(), e);
        }
        return null;
    }

    public String uploadImage(String filenameOrUrl) {
        if (filenameOrUrl == null || filenameOrUrl.isBlank()) {
            return null;
        }
        if (filenameOrUrl.startsWith("http://") || filenameOrUrl.startsWith("https://")) {
            return filenameOrUrl;
        }
        return filenameOrUrl;
    }
}
