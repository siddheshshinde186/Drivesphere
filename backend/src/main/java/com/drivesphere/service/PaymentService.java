package com.drivesphere.service;

import com.drivesphere.dto.PaymentOrderResponse;
import com.drivesphere.dto.PaymentVerifyRequest;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.PaymentRepository;
import com.drivesphere.repository.DriverRepository;
import com.drivesphere.repository.VehicleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final RazorpayClient razorpayClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private VehicleService vehicleService;

    public void setVehicleService(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @Transactional
    public PaymentOrderResponse createPaymentOrder(Long bookingId) {
        return createPaymentOrder(bookingId, null, true);
    }

    @Transactional
    public PaymentOrderResponse createPaymentOrder(Long bookingId, Long currentUserId, boolean isStaff) {
        long startTime = System.currentTimeMillis();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!isStaff && currentUserId != null) {
            if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Unauthorized: You do not own this booking.");
            }
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED || booking.getBookingStatus() == BookingStatus.EXPIRED) {
            throw new IllegalStateException("Cannot initiate payment for a cancelled or expired booking.");
        }

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Booking is already paid.");
        }

        // Reuse recent pending payment order if matching amount exists to prevent redundant DB writes & order thrashing
        Optional<Payment> existingPending = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatus.PENDING);
        if (existingPending.isPresent()) {
            Payment pendingPayment = existingPending.get();
            if (pendingPayment.getAmount().compareTo(booking.getTotalAmount()) == 0 &&
                    pendingPayment.getGatewayOrderId() != null &&
                    pendingPayment.getCreatedAt() != null &&
                    pendingPayment.getCreatedAt().isAfter(java.time.Instant.now().minus(java.time.Duration.ofMinutes(15)))) {

                PaymentOrderResponse existingResponse = PaymentOrderResponse.builder()
                        .orderId(pendingPayment.getGatewayOrderId())
                        .amount(pendingPayment.getAmount())
                        .currency(pendingPayment.getCurrency())
                        .bookingReference(booking.getBookingReference())
                        .keyId(razorpayClient.getKeyId())
                        .gateway(pendingPayment.getGateway())
                        .customerName(booking.getCustomer() != null ? booking.getCustomer().getFullName() : null)
                        .customerEmail(booking.getCustomer() != null ? booking.getCustomer().getEmail() : null)
                        .customerPhone(booking.getCustomer() != null ? booking.getCustomer().getPhoneNumber() : null)
                        .build();

                long duration = System.currentTimeMillis() - startTime;
                log.info("[CHECKOUT_PERF] Reused existing active payment order {} for booking {} in {} ms",
                        pendingPayment.getGatewayOrderId(), bookingId, duration);
                return existingResponse;
            }
        }

        PaymentOrderResponse response = razorpayClient.createOrder(
                booking.getTotalAmount(),
                "INR",
                booking.getBookingReference()
        );

        if (booking.getCustomer() != null) {
            response.setCustomerName(booking.getCustomer().getFullName());
            response.setCustomerEmail(booking.getCustomer().getEmail());
            response.setCustomerPhone(booking.getCustomer().getPhoneNumber());
        }

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(booking.getTotalAmount())
                .currency("INR")
                .gateway(response.getGateway())
                .gatewayOrderId(response.getOrderId())
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[CHECKOUT_PERF] Payment order created in {} ms (orderId: {}, bookingId: {}, amount: INR {})",
                duration, response.getOrderId(), bookingId, booking.getTotalAmount());

        return response;
    }

    @Transactional
    public boolean verifyPayment(PaymentVerifyRequest request) {
        return verifyPayment(request, null, true);
    }

    @Transactional
    public boolean verifyPayment(PaymentVerifyRequest request, Long currentUserId, boolean isStaff) {
        if (request.getBookingId() == null || request.getOrderId() == null || request.getOrderId().isBlank()) {
            throw new IllegalArgumentException("Booking ID and Order ID are required.");
        }

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        if (!isStaff && currentUserId != null) {
            if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Unauthorized: You do not own this booking.");
            }
        }

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Booking {} is already verified and marked as PAID.", booking.getBookingReference());
            return true;
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED || booking.getBookingStatus() == BookingStatus.EXPIRED) {
            throw new IllegalStateException("Cannot verify payment for a cancelled or expired booking.");
        }

        // Verify order exists in repository
        Payment payment = paymentRepository.findByGatewayOrderId(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("No pending payment record found for order ID: " + request.getOrderId()));

        // Verify order belongs to this booking
        if (payment.getBooking() == null || !payment.getBooking().getId().equals(booking.getId())) {
            throw new IllegalArgumentException("Payment order does not belong to booking " + booking.getId());
        }

        // Verify payment amount matches booking total
        if (payment.getAmount() != null && booking.getTotalAmount() != null && payment.getAmount().compareTo(booking.getTotalAmount()) != 0) {
            throw new IllegalStateException("Payment amount mismatch: expected ₹" + booking.getTotalAmount() + " but payment record is ₹" + payment.getAmount());
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Payment record for order {} is already marked as PAID.", request.getOrderId());
            return true;
        }

        boolean isMockOrder = request.getOrderId().startsWith("order_mock_") && payment.getGateway() == PaymentGateway.MOCK;
        boolean isValid = false;

        if (isMockOrder) {
            if (razorpayClient.isAllowMock()) {
                isValid = true;
            } else {
                throw new IllegalStateException("Mock payments are strictly forbidden in production.");
            }
        } else {
            // NEVER trust request.isMockApproval() - verify real HMAC signature
            if (request.getSignature() == null || request.getSignature().isBlank()
                    || request.getPaymentId() == null || request.getPaymentId().isBlank()) {
                log.warn("Missing payment ID or signature for order {}", request.getOrderId());
                return false;
            }
            isValid = razorpayClient.verifySignature(
                    request.getOrderId(),
                    request.getPaymentId(),
                    request.getSignature()
            );
        }

        if (!isValid) {
            log.warn("Payment signature verification failed for booking: {}", booking.getBookingReference());
            return false;
        }

        // Update Payment record
        payment.setGatewayPaymentId(request.getPaymentId() != null ? request.getPaymentId() : ("pay_" + request.getOrderId()));
        payment.setGatewaySignature(request.getSignature());
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        // Update Booking
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // Lock vehicle as booked
        Vehicle vehicle = booking.getVehicle();
        if (vehicle != null) {
            vehicle.setStatus(VehicleStatus.BOOKED);
            vehicleRepository.save(vehicle);
        }

        // If driver assigned, reserve driver
        if (booking.getDriver() != null) {
            Driver driver = booking.getDriver();
            driver.setAvailable(false);
            driverRepository.save(driver);
        }

        notificationService.createNotification(
                booking.getCustomer(),
                "Payment Successful: ₹" + booking.getTotalAmount(),
                "Your payment of ₹" + booking.getTotalAmount() + " was successfully verified. Booking " + booking.getBookingReference() + " is now CONFIRMED!",
                NotificationType.PAYMENT,
                "BOOKING",
                booking.getId()
        );

        return true;
    }

    @Transactional
    public java.math.BigDecimal calculateRefundAmount(Booking booking) {
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            return java.math.BigDecimal.ZERO;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime start = booking.getStartDateTime();

        if (start == null || now.isAfter(start)) {
            return java.math.BigDecimal.ZERO;
        }

        long hoursUntilStart = java.time.Duration.between(now, start).toHours();
        if (hoursUntilStart >= 24) {
            // Full refund if cancelled more than 24 hours in advance
            return booking.getTotalAmount();
        } else if (hoursUntilStart >= 2) {
            // 80% partial refund if cancelled between 2 and 24 hours in advance
            return booking.getTotalAmount().multiply(java.math.BigDecimal.valueOf(0.80)).setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            // 0% refund if cancelled under 2 hours before trip
            return java.math.BigDecimal.ZERO;
        }
    }

    @Transactional
    public boolean processRefundForBooking(Booking booking, String reason) {
        return processRefundForBooking(booking, booking.getTotalAmount(), reason);
    }

    @Transactional
    public boolean processRefundForBooking(Booking booking, java.math.BigDecimal refundAmount, String reason) {
        log.info("Processing refund of ₹{} for booking {} (Reason: {})", refundAmount, booking.getBookingReference(), reason);

        Payment payment = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                booking.getId(), PaymentStatus.PAID
        ).orElse(null);

        if (payment == null) {
            log.warn("No PAID payment record found to refund for booking {}", booking.getBookingReference());
            return false;
        }

        String paymentId = payment.getGatewayPaymentId();
        if (paymentId == null || paymentId.isBlank()) {
            paymentId = payment.getGatewayOrderId();
        }

        RazorpayClient.RefundResult result = razorpayClient.processRefund(
                paymentId,
                refundAmount,
                reason
        );

        payment.setRefundId(result.getRefundId());
        payment.setRefundAmount(result.getAmount());
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        booking.setPaymentStatus(payment.getStatus());
        bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getCustomer(),
                "Refund Initiated: ₹" + refundAmount,
                "A refund of ₹" + refundAmount + " has been initiated for booking " + booking.getBookingReference() + ". Reference: " + result.getRefundId(),
                NotificationType.PAYMENT,
                "BOOKING",
                booking.getId()
        );

        return true;
    }

    @Transactional
    public boolean handleWebhook(String rawPayload, String signature) {
        log.info("Received Razorpay webhook event");

        if (!razorpayClient.isAllowMock()) {
            if (signature == null || signature.isBlank()) {
                log.error("Missing required webhook signature in production mode.");
                return false;
            }
            boolean valid = razorpayClient.verifyWebhookSignature(rawPayload, signature);
            if (!valid) {
                log.error("Webhook signature mismatch or invalid.");
                return false;
            }
        } else {
            if (signature != null && !signature.isBlank()) {
                boolean valid = razorpayClient.verifyWebhookSignature(rawPayload, signature);
                if (!valid) {
                    log.error("Webhook signature mismatch.");
                    return false;
                }
            }
        }

        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(rawPayload);
            String event = root.path("event").asText("");
            log.info("Processing verified Razorpay webhook event: {}", event);

            com.fasterxml.jackson.databind.JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String orderId = paymentEntity.path("order_id").asText(null);
            String paymentId = paymentEntity.path("id").asText(null);

            if ("payment.captured".equalsIgnoreCase(event) || "order.paid".equalsIgnoreCase(event)) {
                if (orderId != null) {
                    paymentRepository.findByGatewayOrderId(orderId).ifPresent(payment -> {
                        if (payment.getStatus() == PaymentStatus.PAID) {
                            log.info("Duplicate webhook: Order {} already recorded as PAID. Skipping duplicate processing.", orderId);
                            return;
                        }

                        payment.setStatus(PaymentStatus.PAID);
                        payment.setGatewayPaymentId(paymentId);
                        payment.setRawWebhookPayload(rawPayload);
                        paymentRepository.save(payment);

                        Booking booking = payment.getBooking();
                        if (booking != null && booking.getPaymentStatus() != PaymentStatus.PAID) {
                            booking.setPaymentStatus(PaymentStatus.PAID);
                            booking.setBookingStatus(BookingStatus.CONFIRMED);
                            bookingRepository.save(booking);

                            if (booking.getVehicle() != null) {
                                booking.getVehicle().setStatus(VehicleStatus.BOOKED);
                                vehicleRepository.save(booking.getVehicle());
                            }
                            if (booking.getDriver() != null) {
                                booking.getDriver().setAvailable(false);
                                driverRepository.save(booking.getDriver());
                            }

                            notificationService.createNotification(
                                    booking.getCustomer(),
                                    "Payment Confirmed via Webhook",
                                    "Your payment of ₹" + payment.getAmount() + " has been confirmed for " + booking.getBookingReference(),
                                    NotificationType.PAYMENT,
                                    "BOOKING",
                                    booking.getId()
                            );
                        }
                    });
                }
            } else if ("refund.processed".equalsIgnoreCase(event)) {
                com.fasterxml.jackson.databind.JsonNode refundEntity = root.path("payload").path("refund").path("entity");
                String refundPaymentId = refundEntity.path("payment_id").asText(null);
                String refundId = refundEntity.path("id").asText(null);
                long amountPaise = refundEntity.path("amount").asLong(0);
                BigDecimal refundAmount = BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100));

                if (refundPaymentId != null) {
                    paymentRepository.findByGatewayPaymentId(refundPaymentId).ifPresent(payment -> {
                        payment.setStatus(PaymentStatus.REFUNDED);
                        payment.setRefundId(refundId);
                        payment.setRefundAmount(refundAmount);
                        payment.setRefundStatus("processed");
                        paymentRepository.save(payment);

                        boolean isFullRefund = payment.getAmount() == null || refundAmount.compareTo(payment.getAmount()) >= 0;

                        Booking booking = payment.getBooking();
                        if (booking != null) {
                            if (isFullRefund) {
                                booking.setPaymentStatus(PaymentStatus.REFUNDED);
                                if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
                                    booking.setBookingStatus(BookingStatus.CANCELLED);
                                    if (booking.getVehicle() != null) {
                                        if (vehicleService != null) {
                                            vehicleService.reconcileVehicleOperationalStatus(booking.getVehicle());
                                        } else {
                                            booking.getVehicle().setStatus(VehicleStatus.AVAILABLE);
                                            booking.getVehicle().setAvailable(true);
                                            vehicleRepository.save(booking.getVehicle());
                                        }
                                    }
                                }
                            }
                            bookingRepository.save(booking);
                        }
                        log.info("Reconciled refund {} (₹{}) for payment {} and booking {}", refundId, refundAmount, refundPaymentId, booking != null ? booking.getBookingReference() : "N/A");
                    });
                }
            } else if ("refund.failed".equalsIgnoreCase(event)) {
                com.fasterxml.jackson.databind.JsonNode refundEntity = root.path("payload").path("refund").path("entity");
                String refundPaymentId = refundEntity.path("payment_id").asText(null);
                String refundId = refundEntity.path("id").asText(null);
                if (refundPaymentId != null) {
                    paymentRepository.findByGatewayPaymentId(refundPaymentId).ifPresent(payment -> {
                        payment.setRefundStatus("failed");
                        paymentRepository.save(payment);
                        log.warn("Refund {} failed for payment {}", refundId, refundPaymentId);
                    });
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
            return false;
        }
    }
}
