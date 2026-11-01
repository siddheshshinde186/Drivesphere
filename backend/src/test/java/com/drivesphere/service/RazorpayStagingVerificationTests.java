package com.drivesphere.service;

import com.drivesphere.entity.*;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RazorpayStagingVerificationTests {

    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private VehicleRepository vehicleRepository;
    private DriverRepository driverRepository;
    private NotificationService notificationService;
    private VehicleService vehicleService;
    private RazorpayClient razorpayClient;

    private PaymentService paymentService;

    private final String testKeyId = "rzp_test_stage123456789";
    private final String testKeySecret = "stageKeySecret9876543210";
    private final String testWebhookSecret = "stageWebhookSecretKey123";

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        driverRepository = mock(DriverRepository.class);
        notificationService = mock(NotificationService.class);
        vehicleService = mock(VehicleService.class);

        razorpayClient = new RazorpayClient();
        ReflectionTestUtils.setField(razorpayClient, "keyId", testKeyId);
        ReflectionTestUtils.setField(razorpayClient, "keySecret", testKeySecret);
        ReflectionTestUtils.setField(razorpayClient, "webhookSecret", testWebhookSecret);
        ReflectionTestUtils.setField(razorpayClient, "allowMock", true);

        paymentService = new PaymentService(
                bookingRepository,
                paymentRepository,
                vehicleRepository,
                driverRepository,
                razorpayClient,
                notificationService
        );
        paymentService.setVehicleService(vehicleService);
    }

    private String calculateHmac(String data, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    @DisplayName("Razorpay Staging 1: Order creation returns proper key and testMode true for rzp_test keys")
    void testCreateOrder_ConfiguredTestKeys() {
        var order = razorpayClient.createOrder(BigDecimal.valueOf(2500.00), "INR", "BK-STAGE-001");
        assertNotNull(order);
        assertNotNull(order.getOrderId());
        assertEquals(BigDecimal.valueOf(2500.00), order.getAmount());
        assertTrue(order.isTestMode());
    }

    @Test
    @DisplayName("Razorpay Staging 2: Valid payment signature passes HMAC-SHA256 verification")
    void testVerifySignature_ValidHmac() throws Exception {
        String orderId = "order_stage_123";
        String paymentId = "pay_stage_456";
        String validSig = calculateHmac(orderId + "|" + paymentId, testKeySecret);

        assertTrue(razorpayClient.verifySignature(orderId, paymentId, validSig));
    }

    @Test
    @DisplayName("Razorpay Staging 3: Tampered payment signature fails HMAC-SHA256 verification")
    void testVerifySignature_TamperedHmac() {
        String orderId = "order_stage_123";
        String paymentId = "pay_stage_456";
        String badSig = "bad_signature_abcdef123456";

        assertFalse(razorpayClient.verifySignature(orderId, paymentId, badSig));
    }

    @Test
    @DisplayName("Razorpay Staging 4: Webhook signature verification validates authenticity")
    void testVerifyWebhookSignature() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String validSig = calculateHmac(payload, testWebhookSecret);

        assertTrue(razorpayClient.verifyWebhookSignature(payload, validSig));
        assertFalse(razorpayClient.verifyWebhookSignature(payload, "invalid_sig"));
    }

    @Test
    @DisplayName("Razorpay Staging 5: Duplicate webhook handling is strictly idempotent")
    void testWebhook_DuplicateOrderPaid_IsIdempotent() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_dup_999\",\"id\":\"pay_dup_999\"}}}}";
        String sig = calculateHmac(payload, testWebhookSecret);

        Payment alreadyPaid = Payment.builder()
                .status(PaymentStatus.PAID)
                .gatewayOrderId("order_dup_999")
                .amount(BigDecimal.valueOf(3500.00))
                .build();
        alreadyPaid.setId(101L);

        when(paymentRepository.findByGatewayOrderId("order_dup_999")).thenReturn(Optional.of(alreadyPaid));

        boolean handled = paymentService.handleWebhook(payload, sig);
        assertTrue(handled);

        // Verification: Never updates or triggers secondary notifications on duplicate
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Razorpay Staging 6: Full refund reconciles payment, booking, and releases vehicle")
    void testWebhook_FullRefundReconcilesBookingAndVehicle() throws Exception {
        String payload = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"payment_id\":\"pay_rfnd_111\",\"id\":\"rfnd_111\",\"amount\":400000}}}}";
        String sig = calculateHmac(payload, testWebhookSecret);

        Vehicle vehicle = Vehicle.builder().status(VehicleStatus.BOOKED).available(false).build();
        vehicle.setId(50L);

        Booking booking = Booking.builder()
                .bookingReference("BK-RFND-111")
                .bookingStatus(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .vehicle(vehicle)
                .build();
        booking.setId(201L);

        Payment payment = Payment.builder()
                .gatewayPaymentId("pay_rfnd_111")
                .amount(BigDecimal.valueOf(4000.00))
                .status(PaymentStatus.PAID)
                .booking(booking)
                .build();
        payment.setId(301L);

        when(paymentRepository.findByGatewayPaymentId("pay_rfnd_111")).thenReturn(Optional.of(payment));

        boolean handled = paymentService.handleWebhook(payload, sig);
        assertTrue(handled);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals("rfnd_111", payment.getRefundId());
        assertEquals(0, BigDecimal.valueOf(4000).compareTo(payment.getRefundAmount()));
        assertEquals(BookingStatus.CANCELLED, booking.getBookingStatus());
        assertEquals(PaymentStatus.REFUNDED, booking.getPaymentStatus());

        verify(vehicleService, times(1)).reconcileVehicleOperationalStatus(vehicle);
        verify(bookingRepository, times(1)).save(booking);
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    @DisplayName("Razorpay Staging 7: Partial refund reconciles payment amount and status without prematurely cancelling booking")
    void testWebhook_PartialRefundReconcilesPayment() throws Exception {
        // Partial refund of 1000.00 (100000 paise) out of 4000.00 total
        String payload = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"payment_id\":\"pay_part_222\",\"id\":\"rfnd_part_222\",\"amount\":100000}}}}";
        String sig = calculateHmac(payload, testWebhookSecret);

        Booking booking = Booking.builder()
                .bookingReference("BK-PART-222")
                .bookingStatus(BookingStatus.ACTIVE)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        booking.setId(202L);

        Payment payment = Payment.builder()
                .gatewayPaymentId("pay_part_222")
                .amount(BigDecimal.valueOf(4000.00))
                .status(PaymentStatus.PAID)
                .booking(booking)
                .build();
        payment.setId(302L);

        when(paymentRepository.findByGatewayPaymentId("pay_part_222")).thenReturn(Optional.of(payment));

        boolean handled = paymentService.handleWebhook(payload, sig);
        assertTrue(handled);

        assertEquals(0, BigDecimal.valueOf(1000).compareTo(payment.getRefundAmount()));
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals("processed", payment.getRefundStatus());
        // Booking active rental remains active
        assertEquals(BookingStatus.ACTIVE, booking.getBookingStatus());
    }

    @Test
    @DisplayName("Razorpay Staging 8: Failed refund webhook records failed status")
    void testWebhook_FailedRefundRecordsStatus() throws Exception {
        String payload = "{\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"payment_id\":\"pay_fail_333\",\"id\":\"rfnd_fail_333\"}}}}";
        String sig = calculateHmac(payload, testWebhookSecret);

        Payment payment = Payment.builder()
                .gatewayPaymentId("pay_fail_333")
                .status(PaymentStatus.PAID)
                .build();
        payment.setId(303L);

        when(paymentRepository.findByGatewayPaymentId("pay_fail_333")).thenReturn(Optional.of(payment));

        boolean handled = paymentService.handleWebhook(payload, sig);
        assertTrue(handled);

        assertEquals("failed", payment.getRefundStatus());
        verify(paymentRepository, times(1)).save(payment);
    }
}
