package com.drivesphere.repository;

import com.drivesphere.entity.Maintenance;
import com.drivesphere.entity.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {
    List<Maintenance> findByVehicleIdOrderByServiceDateDesc(Long vehicleId);
    List<Maintenance> findByStatus(MaintenanceStatus status);
    long countByStatus(MaintenanceStatus status);

    List<Maintenance> findByVehicleIdAndStatus(Long vehicleId, MaintenanceStatus status);
    Optional<Maintenance> findFirstByVehicleIdAndStatusOrderByServiceDateDesc(Long vehicleId, MaintenanceStatus status);
    long countByVehicleIdAndStatusInAndServiceDateBetween(Long vehicleId, List<MaintenanceStatus> statuses, LocalDate startDate, LocalDate endDate);

    @Query("SELECT m FROM Maintenance m WHERE m.status = 'SCHEDULED' AND m.serviceDate <= :targetDate")
    List<Maintenance> findUpcomingMaintenance(LocalDate targetDate);

    @Query("SELECT COALESCE(SUM(m.cost), 0) FROM Maintenance m")
    BigDecimal sumTotalMaintenanceCost();
}
