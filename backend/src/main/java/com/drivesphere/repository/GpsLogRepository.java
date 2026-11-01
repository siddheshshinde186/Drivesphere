package com.drivesphere.repository;

import com.drivesphere.entity.GpsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GpsLogRepository extends JpaRepository<GpsLog, Long> {
    List<GpsLog> findByTripIdOrderByRecordedAtAsc(Long tripId);
    List<GpsLog> findByVehicleIdOrderByRecordedAtDesc(Long vehicleId);
    Optional<GpsLog> findFirstByTripIdOrderByRecordedAtDesc(Long tripId);
}
