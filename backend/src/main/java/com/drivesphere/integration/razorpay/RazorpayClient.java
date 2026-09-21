package com.drivesphere.integration.razorpay;

import com.drivesphere.dto.PaymentOrderResponse;
import com.drivesphere.entity.PaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay Test Mode Payment Gateway Integration.
 * Free-First fallback provides instant Sandbox Mock checkout when keys are not configured,
 * ensuring seamless demonstration without requiring actual payment merchant accounts.
 */
@Slf4j
@Component
public class RazorpayClient {

    @Value("${drivesphere.razorpay.key-id:}")
    private String keyId;

    @Value("${drivesphere.razorpay.key-secret:}")
    private String keySecret;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && !keyId.equalsIgnoreCase("YOUR_RAZORPAY_KEY_ID")
                && keySecret != null && !keySecret.isBlank() && !keySecret.equalsIgnoreCase("YOUR_RAZORPAY_KEY_SECRET");
    }

    public String getKeyId() {
        return isConfigured() ? keyId : "rzp_test_mock_mode";
    }

    /**
     * Create Razorpay test order, or sandbox mock order if keys are not configured.
     */
    public PaymentOrderResponse createOrder(BigDecimal amount, String currency, String receipt) {
        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

        if (isConfigured()) {
            try {
                String basicAuth = Base64.getEncoder().encodeToString(
                        (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)
                );

                Map<String, Object> body = Map.of(
                        "amount", amountInPaise,
                        "currency", currency != null ? currency : "INR",
                        "receipt", receipt,
                        "payment_capture", 1
                );

                String payload = objectMapper.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.razorpay.com/v1/orders"))
                        .header("Authorization", "Basic " + basicAuth)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    JsonNode node = objectMapper.readTree(response.body());
                    String orderId = node.path("id").asText();

                    return PaymentOrderResponse.builder()
                            .orderId(orderId)
                            .amount(amount)
                            .currency(currency)
                            .keyId(keyId)
                            .bookingReference(receipt)
                            .gateway(PaymentGateway.RAZORPAY)
                            .testMode(true)
                            .build();
                } else {
                    log.warn("Razorpay order API failed with status {}. Falling back to Sandbox Mock.", response.statusCode());
                }
            } catch (Exception e) {
                log.error("Razorpay order creation exception: {}. Using Sandbox Mock.", e.getMessage());
            }
        }

        // Sandbox Mock Mode fallback
        String mockOrderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 14);
        return PaymentOrderResponse.builder()
                .orderId(mockOrderId)
                .amount(amount)
                .currency(currency != null ? currency : "INR")
                .keyId("rzp_test_mock_sandbox")
                .bookingReference(receipt)
                .gateway(PaymentGateway.MOCK)
                .testMode(true)
                .build();
    }

    /**
     * Verify payment signature. In Sandbox Mock mode, always succeeds if mock flag or mock prefix is present.
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        if (orderId != null && orderId.startsWith("order_mock_")) {
            return true; // Mock mode sandbox passes verification
        }

        if (!isConfigured()) {
            return true;
        }

        try {
            String data = orderId + "|" + paymentId;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString().equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Razorpay signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
