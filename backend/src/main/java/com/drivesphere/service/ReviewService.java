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
        if (request.getBookingId() == null) {
            throw new IllegalArgumentException("Booking ID is required.");
        }
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("You can only submit reviews for your own bookings.");
        }

        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Reviews can only be submitted for COMPLETED rentals. Current status: " + booking.getBookingStatus());
        }

        if (reviewRepository.findByBookingId(booking.getId()).isPresent()) {
            throw new IllegalStateException("A review has already been submitted for this booking.");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));

        Vehicle vehicle = booking.getVehicle();
        if (vehicle == null) {
            throw new IllegalStateException("No vehicle associated with booking: " + booking.getId());
        }
        Driver driver = booking.getDriver();

        BigDecimal vehRating = request.getVehicleRating() != null ? request.getVehicleRating() : request.getRating();
        String cleanComment = (request.getComment() != null && !request.getComment().isBlank()) ? request.getComment().trim() : null;

        Review review = Review.builder()
                .booking(booking)
                .customer(customer)
                .vehicle(vehicle)
                .driver(driver)
                .rating(request.getRating())
                .vehicleRating(vehRating)
                .conditionRating(request.getConditionRating())
                .cleanlinessRating(request.getCleanlinessRating())
                .comfortRating(request.getComfortRating())
                .driverRating(request.getDriverRating())
                .comment(cleanComment)
                .build();

        review = reviewRepository.save(review);

        // Recalculate Vehicle and Driver ratings
        recalculateVehicleRating(vehicle);
        if (driver != null) {
            recalculateDriverRating(driver);
        }

        return mapper.toReviewDto(review);
    }

    @Transactional
    public ReviewDto updateReview(Long reviewId, Long customerId, ReviewCreateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));

        if (!review.getCustomer().getId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("You can only edit your own reviews.");
        }

        if (request.getRating() != null) review.setRating(request.getRating());
        if (request.getVehicleRating() != null) {
            review.setVehicleRating(request.getVehicleRating());
        } else if (request.getRating() != null) {
            review.setVehicleRating(request.getRating());
        }
        if (request.getConditionRating() != null) review.setConditionRating(request.getConditionRating());
        if (request.getCleanlinessRating() != null) review.setCleanlinessRating(request.getCleanlinessRating());
        if (request.getComfortRating() != null) review.setComfortRating(request.getComfortRating());
        if (request.getDriverRating() != null) review.setDriverRating(request.getDriverRating());
        if (request.getComment() != null) {
            review.setComment(!request.getComment().isBlank() ? request.getComment().trim() : null);
        }

        review = reviewRepository.save(review);

        recalculateVehicleRating(review.getVehicle());
        if (review.getDriver() != null) {
            recalculateDriverRating(review.getDriver());
        }

        return mapper.toReviewDto(review);
    }

    @Transactional(readOnly = true)
    public ReviewDto getReviewByBooking(Long bookingId, Long currentUserId, boolean isStaff) {
        Review review = reviewRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("No review found for booking id: " + bookingId));
        if (!isStaff && currentUserId != null && !review.getCustomer().getId().equals(currentUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized: You do not own this review.");
        }
        return mapper.toReviewDto(review);
    }

    private void recalculateVehicleRating(Vehicle vehicle) {
        if (vehicle == null) return;
        List<Review> vehicleReviews = reviewRepository.findByVehicleIdOrderByCreatedAtDesc(vehicle.getId());
        if (vehicleReviews.isEmpty()) {
            vehicle.setAverageRating(null);
            vehicle.setTotalRatings(0);
        } else {
            double avgVeh = vehicleReviews.stream()
                    .mapToDouble(r -> (r.getVehicleRating() != null ? r.getVehicleRating() : r.getRating()).doubleValue())
                    .average().orElse(0.0);
            vehicle.setAverageRating(BigDecimal.valueOf(avgVeh).setScale(2, RoundingMode.HALF_UP));
            vehicle.setTotalRatings(vehicleReviews.size());
        }
        vehicleRepository.save(vehicle);
    }

    private void recalculateDriverRating(Driver driver) {
        if (driver == null) return;
        List<Review> driverReviews = reviewRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
        var driverRatingOpt = driverReviews.stream().filter(r -> r.getDriverRating() != null)
                .mapToDouble(r -> r.getDriverRating().doubleValue()).average();
        if (driverRatingOpt.isPresent()) {
            driver.setRating(BigDecimal.valueOf(driverRatingOpt.getAsDouble()).setScale(2, RoundingMode.HALF_UP));
        } else {
            driver.setRating(null);
        }
        driverRepository.save(driver);
        trustScoreService.calculateAndSaveTrustScore(driver);
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

    @Transactional(readOnly = true)
    public List<ReviewDto> getAllReviewsAdmin() {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::toReviewDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReviewDto getReviewById(Long id) {
        return reviewRepository.findById(id)
                .map(mapper::toReviewDto)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));
    }

    @Transactional
    public void deleteReview(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + id));

        Vehicle vehicle = review.getVehicle();
        Driver driver = review.getDriver();

        reviewRepository.delete(review);

        // Recalculate Vehicle average rating
        if (vehicle != null) {
            List<Review> vehicleReviews = reviewRepository.findByVehicleIdOrderByCreatedAtDesc(vehicle.getId());
            if (vehicleReviews.isEmpty()) {
                vehicle.setAverageRating(null);
                vehicle.setTotalRatings(0);
            } else {
                double avgVeh = vehicleReviews.stream().mapToDouble(r -> r.getVehicleRating().doubleValue()).average().orElse(0.0);
                vehicle.setAverageRating(BigDecimal.valueOf(avgVeh).setScale(2, RoundingMode.HALF_UP));
                vehicle.setTotalRatings(vehicleReviews.size());
            }
            vehicleRepository.save(vehicle);
        }

        // Recalculate Driver average rating & Trust Score
        if (driver != null) {
            List<Review> driverReviews = reviewRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId());
            var driverRatingOpt = driverReviews.stream().filter(r -> r.getDriverRating() != null)
                    .mapToDouble(r -> r.getDriverRating().doubleValue()).average();
            if (driverRatingOpt.isPresent()) {
                driver.setRating(BigDecimal.valueOf(driverRatingOpt.getAsDouble()).setScale(2, RoundingMode.HALF_UP));
            } else {
                driver.setRating(null);
            }
            driverRepository.save(driver);
            trustScoreService.calculateAndSaveTrustScore(driver);
        }
    }
}
