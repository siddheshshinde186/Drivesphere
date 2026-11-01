package com.drivesphere.service;

import com.drivesphere.entity.*;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.BookingRepository;
import com.drivesphere.repository.MaintenanceRepository;
import com.drivesphere.repository.VehicleCategoryRepository;
import com.drivesphere.repository.VehicleImageRepository;
import com.drivesphere.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VehicleStateConsistencyTests {

    private VehicleRepository vehicleRepository;
    private VehicleCategoryRepository categoryRepository;
    private VehicleImageRepository vehicleImageRepository;
    private EntityDtoMapper mapper;
    private BookingRepository bookingRepository;
    private MaintenanceRepository maintenanceRepository;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleRepository = mock(VehicleRepository.class);
        categoryRepository = mock(VehicleCategoryRepository.class);
        vehicleImageRepository = mock(VehicleImageRepository.class);
        mapper = mock(EntityDtoMapper.class);
        bookingRepository = mock(BookingRepository.class);
        maintenanceRepository = mock(MaintenanceRepository.class);

        vehicleService = new VehicleService(vehicleRepository, categoryRepository, vehicleImageRepository, mapper);
        vehicleService.setBookingRepository(bookingRepository);
        vehicleService.setMaintenanceRepository(maintenanceRepository);

        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Lifecycle Consistency 1: Inactive or decommissioned vehicle is never restored to AVAILABLE")
    void testReconciliation_WhenVehicleInactive_RemainsInactive() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.INACTIVE)
                .available(false)
                .build();
        vehicle.setId(101L);

        Vehicle result = vehicleService.reconcileVehicleOperationalStatus(vehicle);

        assertEquals(VehicleStatus.INACTIVE, result.getStatus());
        assertFalse(result.isAvailable());
        verify(vehicleRepository, times(1)).save(vehicle);
    }

    @Test
    @DisplayName("Lifecycle Consistency 2: In-progress maintenance prevents restoration to AVAILABLE")
    void testReconciliation_WhenMaintenanceInProgress_RemainsMaintenance() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.BOOKED)
                .available(false)
                .build();
        vehicle.setId(102L);

        Maintenance inProgressJob = Maintenance.builder()
                .vehicle(vehicle)
                .status(MaintenanceStatus.IN_PROGRESS)
                .serviceDate(LocalDate.now())
                .build();
        inProgressJob.setId(501L);

        when(maintenanceRepository.findByVehicleIdAndStatus(102L, MaintenanceStatus.IN_PROGRESS))
                .thenReturn(List.of(inProgressJob));

        Vehicle result = vehicleService.reconcileVehicleOperationalStatus(vehicle);

        assertEquals(VehicleStatus.MAINTENANCE, result.getStatus());
        assertFalse(result.isAvailable());
    }

    @Test
    @DisplayName("Lifecycle Consistency 3: Scheduled maintenance due today prevents restoration to AVAILABLE")
    void testReconciliation_WhenMaintenanceScheduledToday_RemainsMaintenance() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.BOOKED)
                .available(false)
                .build();
        vehicle.setId(103L);

        Maintenance scheduledJob = Maintenance.builder()
                .vehicle(vehicle)
                .status(MaintenanceStatus.SCHEDULED)
                .serviceDate(LocalDate.now())
                .build();
        scheduledJob.setId(502L);

        when(maintenanceRepository.findByVehicleIdAndStatus(103L, MaintenanceStatus.IN_PROGRESS))
                .thenReturn(Collections.emptyList());
        when(maintenanceRepository.findByVehicleIdAndStatus(103L, MaintenanceStatus.SCHEDULED))
                .thenReturn(List.of(scheduledJob));

        Vehicle result = vehicleService.reconcileVehicleOperationalStatus(vehicle);

        assertEquals(VehicleStatus.MAINTENANCE, result.getStatus());
        assertFalse(result.isAvailable());
    }

    @Test
    @DisplayName("Lifecycle Consistency 4: Ongoing active rental retains RENTED status")
    void testReconciliation_WhenActiveRentalExists_SetsRented() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.BOOKED)
                .available(false)
                .build();
        vehicle.setId(104L);

        Booking activeRental = Booking.builder()
                .vehicle(vehicle)
                .bookingStatus(BookingStatus.ACTIVE)
                .build();
        activeRental.setId(901L);

        when(maintenanceRepository.findByVehicleIdAndStatus(eq(104L), any(MaintenanceStatus.class)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByVehicleIdAndBookingStatusIn(104L, List.of(BookingStatus.ACTIVE)))
                .thenReturn(List.of(activeRental));

        Vehicle result = vehicleService.reconcileVehicleOperationalStatus(vehicle);

        assertEquals(VehicleStatus.RENTED, result.getStatus());
        assertFalse(result.isAvailable());
    }

    @Test
    @DisplayName("Lifecycle Consistency 5: Concurrent confirmed booking covering current time retains BOOKED status")
    void testReconciliation_WhenConfirmedBookingCurrent_SetsBooked() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.MAINTENANCE)
                .available(false)
                .build();
        vehicle.setId(105L);

        LocalDateTime now = LocalDateTime.now();
        Booking confirmedBooking = Booking.builder()
                .vehicle(vehicle)
                .bookingStatus(BookingStatus.CONFIRMED)
                .startDateTime(now.minusHours(1))
                .endDateTime(now.plusHours(3))
                .build();
        confirmedBooking.setId(902L);

        when(maintenanceRepository.findByVehicleIdAndStatus(eq(105L), any(MaintenanceStatus.class)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByVehicleIdAndBookingStatusIn(105L, List.of(BookingStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByVehicleIdAndBookingStatusIn(105L, List.of(BookingStatus.CONFIRMED, BookingStatus.HELD, BookingStatus.PENDING)))
                .thenReturn(List.of(confirmedBooking));

        Vehicle result = vehicleService.reconcileVehicleOperationalStatus(vehicle);

        assertEquals(VehicleStatus.BOOKED, result.getStatus());
        assertFalse(result.isAvailable());
    }

    @Test
    @DisplayName("Lifecycle Consistency 6: Restores to AVAILABLE only when all operational invariants pass")
    void testReconciliation_WhenNoConflicts_RestoresAvailable() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.BOOKED)
                .available(false)
                .build();
        vehicle.setId(106L);

        when(maintenanceRepository.findByVehicleIdAndStatus(eq(106L), any(MaintenanceStatus.class)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByVehicleIdAndBookingStatusIn(eq(106L), anyList()))
                .thenReturn(Collections.emptyList());

        Vehicle result = vehicleService.reconcileVehicleOperationalStatus(vehicle);

        assertEquals(VehicleStatus.AVAILABLE, result.getStatus());
        assertTrue(result.isAvailable());
    }

    @Test
    @DisplayName("Lifecycle Consistency 7: Overdue service sets status to MAINTENANCE and available to false")
    void testReconciliation_WhenOverdueService_SetsMaintenanceAndUnavailable() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.BOOKED)
                .available(false)
                .build();
        vehicle.setId(107L);

        MaintenanceService maintServiceMock = mock(MaintenanceService.class);
        when(maintServiceMock.calculateServiceStatus(vehicle))
                .thenReturn(com.drivesphere.entity.VehicleServiceStatus.OVERDUE);
        vehicleService.setMaintenanceService(maintServiceMock);

        when(maintenanceRepository.findByVehicleIdAndStatus(eq(107L), any(MaintenanceStatus.class)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByVehicleIdAndBookingStatusIn(eq(107L), anyList()))
                .thenReturn(Collections.emptyList());

        Vehicle result = vehicleService.reconcileVehicleOperationalStatus(vehicle);

        assertEquals(VehicleStatus.MAINTENANCE, result.getStatus());
        assertFalse(result.isAvailable());
    }

    @Test
    @DisplayName("Lifecycle Consistency 8: Toggle status reactivates an INACTIVE vehicle back to AVAILABLE")
    void testToggleVehicleStatus_ReactivatesInactiveVehicle() {
        Vehicle vehicle = Vehicle.builder()
                .status(VehicleStatus.INACTIVE)
                .available(false)
                .build();
        vehicle.setId(108L);

        when(vehicleRepository.findById(108L)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.findByVehicleIdAndStatus(eq(108L), any(MaintenanceStatus.class)))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findByVehicleIdAndBookingStatusIn(eq(108L), anyList()))
                .thenReturn(Collections.emptyList());

        vehicleService.toggleVehicleStatus(108L);

        assertEquals(VehicleStatus.AVAILABLE, vehicle.getStatus());
        assertTrue(vehicle.isAvailable());
    }
}
