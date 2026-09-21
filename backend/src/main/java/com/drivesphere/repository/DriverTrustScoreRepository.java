package com.drivesphere.repository;

import com.drivesphere.entity.Driver;
import com.drivesphere.entity.DriverTrustScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverTrustScoreRepository extends JpaRepository<DriverTrustScore, Long> {
    Optional<DriverTrustScore> findByDriver(Driver driver);
    Optional<DriverTrustScore> findByDriverId(Long driverId);
}
