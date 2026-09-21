package com.drivesphere.repository;

import com.drivesphere.entity.Trip;
import com.drivesphere.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByBookingId(Long bookingId);
    List<Trip> findByStatus(TripStatus status);
}
