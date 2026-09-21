package com.drivesphere.repository;

import com.drivesphere.entity.VehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleCategoryRepository extends JpaRepository<VehicleCategory, Long> {
    Optional<VehicleCategory> findBySlug(String slug);
    Optional<VehicleCategory> findByName(String name);
}
