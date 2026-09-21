package com.drivesphere.service;

import com.drivesphere.dto.PaymentOrderResponse;
import com.drivesphere.dto.PaymentVerifyRequest;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RazorpayClient razorpayClient;
    private final NotificationService notificationService;

    @Transactional
    public PaymentOrderResponse createPaymentOrder(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Booking is already paid.");
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

        return response;
    }

    @Transactional
    public boolean verifyPayment(PaymentVerifyRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        boolean isValid = request.isMockApproval() || razorpayClient.verifySignature(
                request.getOrderId(),
                request.getPaymentId(),
                request.getSignature()
        );

        if (!isValid) {
            log.warn("Payment signature verification failed for booking: {}", booking.getBookingReference());
            return false;
        }

        // Update Payment record
        paymentRepository.findByGatewayOrderId(request.getOrderId()).ifPresentOrElse(payment -> {
            payment.setGatewayPaymentId(request.getPaymentId());
            payment.setGatewaySignature(request.getSignature());
            payment.setStatus(PaymentStatus.PAID);
            paymentRepository.save(payment);
        }, () -> {
            Payment newPayment = Payment.builder()
                    .booking(booking)
                    .amount(booking.getTotalAmount())
                    .currency("INR")
                    .gateway(request.isMockApproval() ? PaymentGateway.MOCK : PaymentGateway.RAZORPAY)
                    .gatewayOrderId(request.getOrderId())
                    .gatewayPaymentId(request.getPaymentId())
                    .status(PaymentStatus.PAID)
                    .build();
            paymentRepository.save(newPayment);
        });

        // Update Booking
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // Lock vehicle as booked
        Vehicle vehicle = booking.getVehicle();
        vehicle.setStatus(VehicleStatus.BOOKED);

        // If driver assigned, reserve driver
        if (booking.getDriver() != null) {
            booking.getDriver().setAvailable(false);
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
}
