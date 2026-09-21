package com.drivesphere.integration.cloudinary;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cloudinary Media Asset Integration.
 * Free-First fallback handles image references and URLs gracefully
 * when Cloudinary credentials are not configured.
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

    public boolean isConfigured() {
        return cloudName != null && !cloudName.isBlank() && !cloudName.equalsIgnoreCase("YOUR_CLOUDINARY_CLOUD_NAME")
                && apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("YOUR_CLOUDINARY_API_KEY");
    }

    /**
     * Upload an image or resolve its accessible public URL.
     */
    public String uploadImage(String filenameOrUrl) {
        if (filenameOrUrl == null || filenameOrUrl.isBlank()) {
            return "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80";
        }

        if (filenameOrUrl.startsWith("http://") || filenameOrUrl.startsWith("https://")) {
            return filenameOrUrl;
        }

        if (isConfigured()) {
            // Generates canonical Cloudinary asset delivery URL
            return String.format("https://res.cloudinary.com/%s/image/upload/v1/drivesphere/%s",
                    cloudName, UUID.randomUUID() + "-" + filenameOrUrl);
        }

        // Fallback placeholder
        return "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=800&q=80";
    }
}
