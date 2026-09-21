package com.drivesphere.repository;

import com.drivesphere.entity.Driver;
import com.drivesphere.entity.DriverVerification;
import com.drivesphere.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverVerificationRepository extends JpaRepository<DriverVerification, Long> {
    Optional<DriverVerification> findByDriver(Driver driver);
    Optional<DriverVerification> findByDriverId(Long driverId);
    List<DriverVerification> findByStatus(VerificationStatus status);
    long countByStatus(VerificationStatus status);
}
