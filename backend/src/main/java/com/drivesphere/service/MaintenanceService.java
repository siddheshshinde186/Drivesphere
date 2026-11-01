package com.drivesphere.service;

import com.drivesphere.dto.MaintenanceCreateRequest;
import com.drivesphere.dto.MaintenanceDto;
import com.drivesphere.dto.VehicleDto;
import com.drivesphere.entity.*;
import com.drivesphere.exception.ResourceNotFoundException;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.MaintenanceRepository;
import com.drivesphere.repository.UserRepository;
import com.drivesphere.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final EntityDtoMapper mapper;

    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private VehicleService vehicleService;

    public void setVehicleService(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @Value("${drivesphere.maintenance.interval-km:10000}")
    private int intervalKm = 10000;

    @Value("${drivesphere.maintenance.interval-months:6}")
    private int intervalMonths = 6;

    @Value("${drivesphere.maintenance.warning-km:1000}")
    private int warningKm = 1000;

    @Value("${drivesphere.maintenance.warning-days:30}")
    private int warningDays = 30;

    @Autowired
    public MaintenanceService(MaintenanceRepository maintenanceRepository,
                              VehicleRepository vehicleRepository,
                              @Autowired(required = false) BookingRepository bookingRepository,
                              @Autowired(required = false) NotificationService notificationService,
                              @Autowired(required = false) UserRepository userRepository,
                              EntityDtoMapper mapper) {
        this.maintenanceRepository = maintenanceRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public MaintenanceService(MaintenanceRepository maintenanceRepository, VehicleRepository vehicleRepository, EntityDtoMapper mapper) {
        this(maintenanceRepository, vehicleRepository, null, null, null, mapper);
    }

    public Map<String, Object> getServicePolicy() {
        return Map.of(
                "intervalKm", intervalKm,
                "intervalMonths", intervalMonths,
                "warningKm", warningKm,
                "warningDays", warningDays
        );
    }

    public VehicleServiceStatus calculateServiceStatus(Vehicle vehicle) {
        if (vehicle == null) return VehicleServiceStatus.NO_BASELINE;
        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            return VehicleServiceStatus.UNDER_MAINTENANCE;
        }

        BigDecimal currentKm = vehicle.getCurrentOdometerKm();
        BigDecimal lastKm = vehicle.getLastServiceOdometerKm();
        LocalDate lastDate = vehicle.getLastServiceDate();

        if (lastKm == null && lastDate == null) {
            Optional<Maintenance> latestCompleted = maintenanceRepository
                    .findFirstByVehicleIdAndStatusOrderByServiceDateDesc(vehicle.getId(), MaintenanceStatus.COMPLETED);
            if (latestCompleted.isPresent()) {
                Maintenance m = latestCompleted.get();
                lastKm = m.getOdometerKm();
                lastDate = m.getServiceDate();
            }
        }

        if (lastKm == null && lastDate == null && currentKm == null) {
            return VehicleServiceStatus.NO_BASELINE;
        }

        BigDecimal baselineKm = lastKm != null ? lastKm : BigDecimal.ZERO;
        BigDecimal kmTraveledSince = (currentKm != null && currentKm.compareTo(baselineKm) >= 0)
                ? currentKm.subtract(baselineKm)
                : BigDecimal.ZERO;
        BigDecimal kmUntilNext = BigDecimal.valueOf(intervalKm).subtract(kmTraveledSince);

        LocalDate baselineDate = lastDate != null ? lastDate
                : (vehicle.getCreatedAt() != null ? LocalDate.ofInstant(vehicle.getCreatedAt(), java.time.ZoneId.systemDefault()) : LocalDate.now());
        LocalDate nextDueDate = baselineDate.plusMonths(intervalMonths);
        long daysUntilNext = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), nextDueDate);

        if (kmUntilNext.compareTo(BigDecimal.ZERO) < 0 || daysUntilNext < 0) {
            return VehicleServiceStatus.OVERDUE;
        }
        if (kmUntilNext.compareTo(BigDecimal.ZERO) == 0 || daysUntilNext == 0) {
            return VehicleServiceStatus.SERVICE_DUE;
        }
        if (kmUntilNext.compareTo(BigDecimal.valueOf(warningKm)) <= 0 || daysUntilNext <= warningDays) {
            return VehicleServiceStatus.SERVICE_DUE_SOON;
        }
        return VehicleServiceStatus.SERVICE_UP_TO_DATE;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> getAllMaintenance() {
        return maintenanceRepository.findAll().stream()
                .map(mapper::toMaintenanceDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> getVehicleMaintenanceHistory(Long vehicleId) {
        return maintenanceRepository.findByVehicleIdOrderByServiceDateDesc(vehicleId).stream()
                .map(mapper::toMaintenanceDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MaintenanceDto> getUpcomingMaintenance() {
        return maintenanceRepository.findUpcomingMaintenance(LocalDate.now().plusDays(14)).stream()
                .map(mapper::toMaintenanceDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MaintenanceDto scheduleMaintenance(MaintenanceCreateRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

        Maintenance maintenance = Maintenance.builder()
                .vehicle(vehicle)
                .serviceType(request.getServiceType())
                .serviceDate(request.getServiceDate())
                .nextServiceDate(request.getNextServiceDate())
                .description(request.getDescription().trim())
                .cost(request.getCost())
                .status(MaintenanceStatus.SCHEDULED)
                .odometerKm(request.getOdometerKm() != null ? request.getOdometerKm() : vehicle.getCurrentOdometerKm())
                .notes(request.getNotes())
                .build();

        maintenance = maintenanceRepository.save(maintenance);

        // Premature locking bug fix: ONLY lock the vehicle as MAINTENANCE if scheduled for today or starting immediately.
        // Future scheduled maintenance appointments leave vehicle operational until appointment arrival.
        if (request.getServiceDate().isEqual(LocalDate.now()) || request.getServiceDate().isBefore(LocalDate.now())) {
            vehicle.setStatus(VehicleStatus.MAINTENANCE);
            vehicle.setAvailable(false);
            vehicleRepository.save(vehicle);
        }

        return mapper.toMaintenanceDto(maintenance);
    }

    @Transactional
    public MaintenanceDto updateMaintenanceStatus(Long id, MaintenanceStatus status, BigDecimal finalCost, String notes) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance record not found with id: " + id));

        maintenance.setStatus(status);
        if (finalCost != null) maintenance.setCost(finalCost);
        if (notes != null) maintenance.setNotes(notes);

        if (status == MaintenanceStatus.IN_PROGRESS) {
            Vehicle vehicle = maintenance.getVehicle();
            vehicle.setStatus(VehicleStatus.MAINTENANCE);
            vehicle.setAvailable(false);
            vehicleRepository.save(vehicle);
        } else if (status == MaintenanceStatus.COMPLETED) {
            Vehicle vehicle = maintenance.getVehicle();
            // Update servicing baseline tracking
            if (maintenance.getOdometerKm() != null) {
                vehicle.setCurrentOdometerKm(maintenance.getOdometerKm());
                vehicle.setLastServiceOdometerKm(maintenance.getOdometerKm());
            } else if (vehicle.getCurrentOdometerKm() != null) {
                vehicle.setLastServiceOdometerKm(vehicle.getCurrentOdometerKm());
            }
            vehicle.setLastServiceDate(maintenance.getServiceDate() != null ? maintenance.getServiceDate() : LocalDate.now());

            // Centralized Operational Status Reconciliation
            if (vehicleService != null) {
                vehicleService.reconcileVehicleOperationalStatus(vehicle);
            } else {
                List<Maintenance> openJobs = maintenanceRepository.findByVehicleIdAndStatus(vehicle.getId(), MaintenanceStatus.IN_PROGRESS);
                boolean hasOtherOpenJobs = openJobs.stream().anyMatch(m -> !m.getId().equals(maintenance.getId()));
                boolean hasActiveBooking = false;
                if (bookingRepository != null) {
                    List<Booking> activeBookings = bookingRepository.findByVehicleIdAndBookingStatusIn(
                            vehicle.getId(), List.of(BookingStatus.ACTIVE, BookingStatus.CONFIRMED)
                    );
                    hasActiveBooking = !activeBookings.isEmpty();
                }

                if (!hasOtherOpenJobs && !hasActiveBooking) {
                    vehicle.setStatus(VehicleStatus.AVAILABLE);
                    vehicle.setAvailable(true);
                } else if (hasActiveBooking) {
                    vehicle.setStatus(VehicleStatus.RENTED);
                    vehicle.setAvailable(false);
                }
                vehicleRepository.save(vehicle);
            }
        }

        return mapper.toMaintenanceDto(maintenanceRepository.save(maintenance));
    }

    @Transactional
    public VehicleDto updateVehicleOdometer(Long vehicleId, BigDecimal newOdometerKm) {
        if (newOdometerKm == null || newOdometerKm.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Odometer reading cannot be negative.");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));

        if (vehicle.getCurrentOdometerKm() != null && newOdometerKm.compareTo(vehicle.getCurrentOdometerKm()) < 0) {
            throw new IllegalArgumentException("New odometer reading (" + newOdometerKm + " km) cannot be less than current odometer reading (" + vehicle.getCurrentOdometerKm() + " km). Non-decreasing validation enforced.");
        }

        vehicle.setCurrentOdometerKm(newOdometerKm);
        Vehicle saved = vehicleRepository.save(vehicle);

        // Check service status and trigger notification if due/due soon
        checkAndDispatchServiceNotification(saved);

        return mapper.toVehicleDto(saved);
    }

    private void checkAndDispatchServiceNotification(Vehicle vehicle) {
        if (notificationService == null || userRepository == null) return;
        VehicleServiceStatus status = calculateServiceStatus(vehicle);
        if (status == VehicleServiceStatus.SERVICE_DUE_SOON || status == VehicleServiceStatus.SERVICE_DUE || status == VehicleServiceStatus.OVERDUE) {
            try {
                List<User> staff = userRepository.findAll().stream()
                        .filter(u -> u.hasRole(RoleType.ROLE_ADMIN) || u.hasRole(RoleType.ROLE_FLEET_MANAGER))
                        .collect(Collectors.toList());

                for (User user : staff) {
                    String title = "Vehicle Servicing Alert: " + vehicle.getBrand() + " " + vehicle.getModel() + " [" + vehicle.getRegistrationNumber() + "]";
                    String message = "Vehicle has reached service milestone: status is " + status + ". Current Odometer: " + vehicle.getCurrentOdometerKm() + " km.";
                    notificationService.createNotification(user, title, message, NotificationType.ALERT, "VEHICLE", vehicle.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to dispatch service notification: {}", e.getMessage());
            }
        }
    }
}
