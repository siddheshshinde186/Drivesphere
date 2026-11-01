package com.drivesphere.service;

import com.drivesphere.dto.InvoiceDto;
import com.drivesphere.entity.Booking;
import com.drivesphere.entity.Payment;
import com.drivesphere.entity.PaymentGateway;
import com.drivesphere.entity.PaymentStatus;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public InvoiceDto generateInvoice(Long bookingId, Long currentUserId, boolean isStaff) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!isStaff && currentUserId != null) {
            boolean isCustomer = booking.getCustomer() != null && booking.getCustomer().getId().equals(currentUserId);
            boolean isDriver = booking.getDriver() != null && booking.getDriver().getUser() != null
                    && booking.getDriver().getUser().getId().equals(currentUserId);

            if (!isCustomer && !isDriver) {
                throw new org.springframework.security.access.AccessDeniedException("Unauthorized: You do not have permission to view this invoice.");
            }
        }

        Payment payment = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.PAID
        ).orElseGet(() -> paymentRepository.findByBookingId(bookingId).stream().findFirst().orElse(null));

        BigDecimal rentalSubtotal = booking.getRentalAmount() != null ? booking.getRentalAmount() : BigDecimal.ZERO;
        BigDecimal driverSubtotal = booking.getDriverAmount() != null ? booking.getDriverAmount() : BigDecimal.ZERO;
        BigDecimal subtotal = rentalSubtotal.add(driverSubtotal);

        BigDecimal totalTax = booking.getTaxAmount() != null ? booking.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal halfTax = totalTax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        LocalDateTime paidTime = null;
        if (payment != null && payment.getCreatedAt() != null) {
            paidTime = LocalDateTime.ofInstant(payment.getCreatedAt(), ZoneId.systemDefault());
        }

        return InvoiceDto.builder()
                .invoiceNumber("INV-" + booking.getBookingReference())
                .invoiceDate(LocalDateTime.now())
                .bookingReference(booking.getBookingReference())
                .bookingStatus(booking.getBookingStatus())
                .customerId(booking.getCustomer() != null ? booking.getCustomer().getId() : null)
                .customerName(booking.getCustomer() != null ? booking.getCustomer().getFullName() : "Valued Customer")
                .customerEmail(booking.getCustomer() != null ? booking.getCustomer().getEmail() : "")
                .customerPhone(booking.getCustomer() != null ? booking.getCustomer().getPhoneNumber() : "")
                .vehicleId(booking.getVehicle() != null ? booking.getVehicle().getId() : null)
                .vehicleName(booking.getVehicle() != null ? booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() : "Vehicle")
                .vehicleRegistrationNumber(booking.getVehicle() != null ? booking.getVehicle().getRegistrationNumber() : "")
                .vehicleCategory(booking.getVehicle() != null && booking.getVehicle().getCategory() != null ? booking.getVehicle().getCategory().getName() : "Standard")
                .driverId(booking.getDriver() != null ? booking.getDriver().getId() : null)
                .driverName(booking.getDriver() != null && booking.getDriver().getUser() != null ? booking.getDriver().getUser().getFullName() : null)
                .driverLicenseNumber(booking.getDriver() != null ? booking.getDriver().getLicenseNumber() : null)
                .pickupLocation(booking.getPickupLocation())
                .dropLocation(booking.getDropLocation())
                .startDateTime(booking.getStartDateTime())
                .endDateTime(booking.getEndDateTime())
                .totalDays(booking.getTotalDays())
                .totalHours(booking.getTotalHours())
                .dailyRentalRate(booking.getVehicle() != null ? booking.getVehicle().getRentalPricePerDay() : BigDecimal.ZERO)
                .rentalSubtotal(rentalSubtotal)
                .driverDailyRate(booking.getDriver() != null ? booking.getDriver().getDailyRate() : BigDecimal.ZERO)
                .driverSubtotal(driverSubtotal)
                .subtotal(subtotal)
                .taxRate(BigDecimal.valueOf(0.18))
                .cgstRate(BigDecimal.valueOf(0.09))
                .cgstAmount(halfTax)
                .sgstRate(BigDecimal.valueOf(0.09))
                .sgstAmount(halfTax)
                .totalTaxAmount(totalTax)
                .discountAmount(booking.getDiscountAmount() != null ? booking.getDiscountAmount() : BigDecimal.ZERO)
                .depositAmount(booking.getDepositAmount() != null ? booking.getDepositAmount() : BigDecimal.ZERO)
                .totalAmount(booking.getTotalAmount())
                .paymentStatus(booking.getPaymentStatus())
                .paymentGateway(payment != null ? payment.getGateway() : PaymentGateway.RAZORPAY)
                .gatewayOrderId(payment != null ? payment.getGatewayOrderId() : null)
                .gatewayPaymentId(payment != null ? payment.getGatewayPaymentId() : null)
                .paidAt(paidTime)
                .refundId(payment != null ? payment.getRefundId() : null)
                .refundAmount(payment != null ? payment.getRefundAmount() : null)
                .refundStatus(payment != null ? payment.getRefundStatus() : null)
                .build();
    }
}
