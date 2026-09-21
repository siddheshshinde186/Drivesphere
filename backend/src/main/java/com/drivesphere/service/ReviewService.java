package com.drivesphere.service;

import com.drivesphere.dto.ReviewCreateRequest;
import com.drivesphere.dto.ReviewDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final DriverTrustScoreService trustScoreService;
    private final EntityDtoMapper mapper;

    @Transactional
    public ReviewDto submitReview(Long customerId, ReviewCreateRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Reviews can only be submitted for COMPLETED rentals.");
        }

        if (reviewRepository.findByBookingId(booking.getId()).isPresent()) {
            throw new IllegalStateException("A review has already been submitted for this booking.");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        Vehicle vehicle = booking.getVehicle();
        Driver driver = booking.getDriver();

        Review review = Review.builder()
                .booking(booking)
                .customer(customer)
                .vehicle(vehicle)
                .driver(driver)
                .rating(request.getRating())
                .vehicleRating(request.getVehicleRating())
                .driverRating(request.getDriverRating())
                .comment(request.getComment().trim())
                .build();

        review = reviewRepository.save(review);

        // Update Vehicle average rating
        List<Review> vehicleReviews = reviewRepository.findByVehicleIdOrderByCreatedAtDesc(vehicle.getId());
        double avgVeh = vehicleReviews.stream().mapToDouble(r -> r.getVehicleRating().doubleValue()).average().orElse(5.0);
        vehicle.setAverageRating(BigDecimal.valueOf(avgVeh).setScale(2, RoundingMode.HALF_UP));
        vehicle.setTotalRatings(vehicleReviews.size());
        vehicleRepository.save(vehicle);

        // Update Driver average rating & recalculate Driver Trust Score
        if (driver != null && request.getDriverRating() != null) {
            List<Review> driverReviews = reviewRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
            double avgDrv = driverReviews.stream().filter(r -> r.getDriverRating() != null)
                    .mapToDouble(r -> r.getDriverRating().doubleValue()).average().orElse(5.0);
            driver.setRating(BigDecimal.valueOf(avgDrv).setScale(2, RoundingMode.HALF_UP));
            driverRepository.save(driver);
            trustScoreService.calculateAndSaveTrustScore(driver);
        }

        return mapper.toReviewDto(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getVehicleReviews(Long vehicleId) {
        return reviewRepository.findByVehicleIdOrderByCreatedAtDesc(vehicleId).stream()
                .map(mapper::toReviewDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getDriverReviews(Long driverId) {
        return reviewRepository.findByDriverIdOrderByCreatedAtDesc(driverId).stream()
                .map(mapper::toReviewDto)
                .collect(Collectors.toList());
    }
}
