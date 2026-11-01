package com.drivesphere.integration.razorpay;

import com.drivesphere.dto.PaymentOrderResponse;
import com.drivesphere.entity.PaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Production-grade Razorpay Payment Gateway Client.
 * Handles orders, signature verification, refunds, and webhook signatures.
 * Fails fast in production if credentials are not configured.
 */
@Slf4j
@Component
public class RazorpayClient {

    @Value("${drivesphere.razorpay.key-id:}")
    private String keyId;

    @Value("${drivesphere.razorpay.key-secret:}")
    private String keySecret;

    @Value("${drivesphere.razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${drivesphere.payment.allow-mock:false}")
    private boolean allowMock;

    public boolean isAllowMock() {
        return allowMock;
    }

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

    @Data
    @Builder
    public static class RefundResult {
        private String refundId;
        private String status;
        private BigDecimal amount;
        private PaymentGateway gateway;
    }

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && !keyId.equalsIgnoreCase("YOUR_RAZORPAY_KEY_ID")
                && keySecret != null && !keySecret.isBlank() && !keySecret.equalsIgnoreCase("YOUR_RAZORPAY_KEY_SECRET");
    }

    public String getKeyId() {
        if (isConfigured()) {
            return keyId;
        }
        if (allowMock) {
            return "rzp_test_mock_sandbox";
        }
        throw new IllegalStateException("Razorpay credentials are not configured in production environment.");
    }

    /**
     * Create Razorpay test order, or sandbox mock order in dev/test if keys are unconfigured.
     */
    public PaymentOrderResponse createOrder(BigDecimal amount, String currency, String receipt) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }

        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
        String chosenCurrency = currency != null && !currency.isBlank() ? currency : "INR";

        if (isConfigured()) {
            try {
                String basicAuth = Base64.getEncoder().encodeToString(
                        (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)
                );

                Map<String, Object> body = Map.of(
                        "amount", amountInPaise,
                        "currency", chosenCurrency,
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

                HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    JsonNode node = objectMapper.readTree(response.body());
                    String orderId = node.path("id").asText();

                    boolean isTest = (keyId != null && keyId.startsWith("rzp_test_")) || (orderId != null && orderId.startsWith("order_mock_"));
                    return PaymentOrderResponse.builder()
                            .orderId(orderId)
                            .amount(amount)
                            .currency(chosenCurrency)
                            .keyId(keyId)
                            .bookingReference(receipt)
                            .gateway(PaymentGateway.RAZORPAY)
                            .testMode(isTest)
                            .build();
                } else {
                    log.error("Razorpay order API failed with HTTP {}: {}", response.statusCode(), response.body());
                    if (!allowMock) {
                        throw new IllegalStateException("Razorpay order creation rejected by gateway: " + response.body());
                    }
                }
            } catch (Exception e) {
                log.error("Razorpay order creation exception: {}", e.getMessage());
                if (!allowMock) {
                    throw new IllegalStateException("Failed to communicate with Razorpay: " + e.getMessage(), e);
                }
            }
        }

        if (!allowMock) {
            throw new IllegalStateException("Razorpay is not configured and mock mode is disabled in production.");
        }

        // Sandbox Mock Mode for dev/test only
        String mockOrderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 14);
        return PaymentOrderResponse.builder()
                .orderId(mockOrderId)
                .amount(amount)
                .currency(chosenCurrency)
                .keyId("rzp_test_mock_sandbox")
                .bookingReference(receipt)
                .gateway(PaymentGateway.MOCK)
                .testMode(true)
                .build();
    }

    /**
     * Verify payment signature with HMAC-SHA256.
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        if (orderId != null && orderId.startsWith("order_mock_")) {
            if (allowMock) {
                return true;
            }
            throw new IllegalStateException("Mock payments are forbidden in production.");
        }

        if (!isConfigured()) {
            if (allowMock) {
                return true;
            }
            throw new IllegalStateException("Razorpay credentials are not configured in production environment.");
        }

        if (orderId == null || paymentId == null || signature == null) {
            return false;
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
            log.error("Razorpay signature verification exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Issues an actual refund call to the Razorpay Payments API.
     */
    public RefundResult processRefund(String paymentId, BigDecimal amount, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive.");
        }

        long amountInPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();

        if (isConfigured()) {
            try {
                String basicAuth = Base64.getEncoder().encodeToString(
                        (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)
                );

                Map<String, Object> body = new HashMap<>();
                body.put("amount", amountInPaise);
                if (notes != null && !notes.isBlank()) {
                    body.put("notes", Map.of("reason", notes));
                }

                String payload = objectMapper.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.razorpay.com/v1/payments/" + paymentId + "/refund"))
                        .header("Authorization", "Basic " + basicAuth)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    JsonNode node = objectMapper.readTree(response.body());
                    return RefundResult.builder()
                            .refundId(node.path("id").asText())
                            .status(node.path("status").asText("processed"))
                            .amount(amount)
                            .gateway(PaymentGateway.RAZORPAY)
                            .build();
                } else {
                    log.error("Razorpay refund failed with HTTP {}: {}", response.statusCode(), response.body());
                    if (!allowMock) {
                        throw new IllegalStateException("Razorpay refund rejected: " + response.body());
                    }
                }
            } catch (Exception e) {
                log.error("Razorpay refund exception: {}", e.getMessage());
                if (!allowMock) {
                    throw new IllegalStateException("Failed to communicate with Razorpay refund API: " + e.getMessage(), e);
                }
            }
        }

        if (!allowMock) {
            throw new IllegalStateException("Razorpay credentials missing; cannot process refund in production.");
        }

        // Mock refund in development/test
        return RefundResult.builder()
                .refundId("rfnd_mock_" + UUID.randomUUID().toString().substring(0, 14))
                .status("processed")
                .amount(amount)
                .gateway(PaymentGateway.MOCK)
                .build();
    }

    /**
     * Validates incoming webhook payload against Razorpay webhook secret using HMAC-SHA256.
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Webhook secret not configured. Signature verification disabled.");
            return false;
        }

        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString().equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Webhook signature check error: {}", e.getMessage());
            return false;
        }
    }
}
