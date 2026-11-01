package com.drivesphere.dto;

import com.drivesphere.entity.BookingStatus;
import com.drivesphere.entity.PaymentGateway;
import com.drivesphere.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {
    private String invoiceNumber;
    private LocalDateTime invoiceDate;
    private String bookingReference;
    private BookingStatus bookingStatus;

    // Customer info
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    // Vehicle info
    private Long vehicleId;
    private String vehicleName;
    private String vehicleRegistrationNumber;
    private String vehicleCategory;

    // Driver info
    private Long driverId;
    private String driverName;
    private String driverLicenseNumber;

    // Itinerary
    private String pickupLocation;
    private String dropLocation;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private Integer totalDays;
    private Integer totalHours;

    // Financial Breakdown
    private BigDecimal dailyRentalRate;
    private BigDecimal rentalSubtotal;
    private BigDecimal driverDailyRate;
    private BigDecimal driverSubtotal;
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal cgstRate;
    private BigDecimal cgstAmount;
    private BigDecimal sgstRate;
    private BigDecimal sgstAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal discountAmount;
    private BigDecimal depositAmount;
    private BigDecimal totalAmount;

    // Payment details
    private PaymentStatus paymentStatus;
    private PaymentGateway paymentGateway;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private LocalDateTime paidAt;

    // Refund details (if any)
    private String refundId;
    private BigDecimal refundAmount;
    private String refundStatus;
}
