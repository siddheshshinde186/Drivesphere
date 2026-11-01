package com.drivesphere.repository;

import com.drivesphere.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VehicleRepositoryJpaIntegrationTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private User testCustomer;
    private Vehicle nexonEv;
    private Vehicle xuv700;
    private Vehicle cityHybrid;
    private Vehicle inactiveSwift;
    private Vehicle maintenanceCreta;

    @BeforeEach
    void setUp() {
        testCustomer = User.builder()
                .email("test.customer@drivesphere.com")
                .passwordHash("$2a$10$hashedPasswordPlaceholder")
                .firstName("Aarav")
                .lastName("Sharma")
                .phoneNumber("9876543210")
                .active(true)
                .build();
        userRepository.save(testCustomer);

        // 1. Available Nexon EV
        nexonEv = Vehicle.builder()
                .registrationNumber("KA-01-EV-2024")
                .brand("Tata")
                .model("Nexon EV Empowered")
                .year(2024)
                .vehicleType(VehicleType.ELECTRIC)
                .fuelType(FuelType.ELECTRIC)
                .transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5)
                .mileage(BigDecimal.valueOf(312.0))
                .rangeKm(312)
                .rentalPricePerDay(BigDecimal.valueOf(2800.0))
                .rentalPricePerHour(BigDecimal.valueOf(200.0))
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .build();
        vehicleRepository.save(nexonEv);

        // 2. Available XUV700 (Diesel, SUV)
        xuv700 = Vehicle.builder()
                .registrationNumber("KA-03-MB-7001")
                .brand("Mahindra")
                .model("XUV700 AX7")
                .year(2023)
                .vehicleType(VehicleType.SUV)
                .fuelType(FuelType.DIESEL)
                .transmission(Transmission.AUTOMATIC)
                .seatingCapacity(7)
                .mileage(BigDecimal.valueOf(14.8))
                .rangeKm(850)
                .rentalPricePerDay(BigDecimal.valueOf(3600.0))
                .rentalPricePerHour(BigDecimal.valueOf(250.0))
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .build();
        vehicleRepository.save(xuv700);

        // 3. Available City Hybrid (Petrol / Hybrid, Sedan)
        cityHybrid = Vehicle.builder()
                .registrationNumber("KA-02-HC-1122")
                .brand("Honda")
                .model("City e:HEV")
                .year(2024)
                .vehicleType(VehicleType.SEDAN)
                .fuelType(FuelType.HYBRID)
                .transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5)
                .mileage(BigDecimal.valueOf(27.1))
                .rangeKm(950)
                .rentalPricePerDay(BigDecimal.valueOf(2400.0))
                .rentalPricePerHour(BigDecimal.valueOf(190.0))
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .build();
        vehicleRepository.save(cityHybrid);

        // 4. Inactive Vehicle (available = true, status = INACTIVE)
        inactiveSwift = Vehicle.builder()
                .registrationNumber("KA-05-SW-9999")
                .brand("Maruti")
                .model("Swift VXi")
                .year(2022)
                .vehicleType(VehicleType.HATCHBACK)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.MANUAL)
                .seatingCapacity(5)
                .mileage(BigDecimal.valueOf(19.0))
                .rentalPricePerDay(BigDecimal.valueOf(1500.0))
                .rentalPricePerHour(BigDecimal.valueOf(120.0))
                .status(VehicleStatus.INACTIVE)
                .available(true)
                .build();
        vehicleRepository.save(inactiveSwift);

        // 5. Maintenance Vehicle (available = false, status = MAINTENANCE)
        maintenanceCreta = Vehicle.builder()
                .registrationNumber("KA-04-HY-3344")
                .brand("Hyundai")
                .model("Creta SX")
                .year(2023)
                .vehicleType(VehicleType.SUV)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5)
                .mileage(BigDecimal.valueOf(16.5))
                .rentalPricePerDay(BigDecimal.valueOf(3000.0))
                .rentalPricePerHour(BigDecimal.valueOf(220.0))
                .status(VehicleStatus.MAINTENANCE)
                .available(false)
                .build();
        vehicleRepository.save(maintenanceCreta);

        entityManager.flush();
    }

    @Test
    @DisplayName("findOperationalAvailableVehicles only returns available=true and status=AVAILABLE")
    void testFindOperationalAvailableVehicles() {
        List<Vehicle> available = vehicleRepository.findOperationalAvailableVehicles();

        assertNotNull(available);
        assertEquals(3, available.size());

        List<String> regNumbers = available.stream().map(Vehicle::getRegistrationNumber).toList();
        assertTrue(regNumbers.contains("KA-01-EV-2024"));
        assertTrue(regNumbers.contains("KA-03-MB-7001"));
        assertTrue(regNumbers.contains("KA-02-HC-1122"));

        assertFalse(regNumbers.contains("KA-05-SW-9999"), "Inactive vehicle must be excluded");
        assertFalse(regNumbers.contains("KA-04-HY-3344"), "Maintenance vehicle must be excluded");
    }

    @Test
    @DisplayName("countOverlappingVehicleBookings detects confirmed bookings and respects date boundaries")
    void testCountOverlappingBookingsConfirmed() {
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(5);

        // Create confirmed booking for Nexon EV
        Booking booking = Booking.builder()
                .bookingReference("BK-TEST-CONF-001")
                .customer(testCustomer)
                .vehicle(nexonEv)
                .startDateTime(start)
                .endDateTime(end)
                .pickupLocation("Bangalore Hub")
                .dropLocation("Mysore Hub")
                .totalDays(3)
                .rentalAmount(BigDecimal.valueOf(8400.0))
                .totalAmount(BigDecimal.valueOf(9912.0))
                .bookingStatus(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        bookingRepository.save(booking);
        entityManager.flush();

        // 1. Exact overlap
        long overlapExact = vehicleRepository.countOverlappingVehicleBookings(nexonEv.getId(), start, end);
        assertEquals(1, overlapExact);

        // 2. Partial overlap (starts during booking)
        long overlapPartial = vehicleRepository.countOverlappingVehicleBookings(
                nexonEv.getId(),
                start.plusDays(1),
                end.plusDays(2));
        assertEquals(1, overlapPartial);

        // 3. Different vehicle (XUV700) should have 0 overlapping bookings
        long overlapDiffVehicle = vehicleRepository.countOverlappingVehicleBookings(xuv700.getId(), start, end);
        assertEquals(0, overlapDiffVehicle);

        // 4. Outside period (before start)
        long overlapBefore = vehicleRepository.countOverlappingVehicleBookings(
                nexonEv.getId(),
                start.minusDays(3),
                start.minusDays(1));
        assertEquals(0, overlapBefore);

        // 5. Outside period (after end)
        long overlapAfter = vehicleRepository.countOverlappingVehicleBookings(
                nexonEv.getId(),
                end.plusDays(1),
                end.plusDays(3));
        assertEquals(0, overlapAfter);
    }

    @Test
    @DisplayName("countOverlappingVehicleBookings includes active holds but excludes expired holds")
    void testCountOverlappingBookingsActiveAndExpiredHolds() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(3);

        // Active hold (expires 15 minutes in future)
        Booking activeHold = Booking.builder()
                .bookingReference("BK-TEST-HOLD-ACTIVE")
                .customer(testCustomer)
                .vehicle(nexonEv)
                .startDateTime(start)
                .endDateTime(end)
                .pickupLocation("Bangalore Hub")
                .dropLocation("Bangalore Hub")
                .totalDays(2)
                .rentalAmount(BigDecimal.valueOf(5600.0))
                .totalAmount(BigDecimal.valueOf(6608.0))
                .bookingStatus(BookingStatus.HELD)
                .paymentStatus(PaymentStatus.PENDING)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        bookingRepository.save(activeHold);

        // Expired hold on XUV700 (expired 10 minutes ago)
        Booking expiredHold = Booking.builder()
                .bookingReference("BK-TEST-HOLD-EXPIRED")
                .customer(testCustomer)
                .vehicle(xuv700)
                .startDateTime(start)
                .endDateTime(end)
                .pickupLocation("Bangalore Hub")
                .dropLocation("Bangalore Hub")
                .totalDays(2)
                .rentalAmount(BigDecimal.valueOf(7200.0))
                .totalAmount(BigDecimal.valueOf(8496.0))
                .bookingStatus(BookingStatus.HELD)
                .paymentStatus(PaymentStatus.PENDING)
                .holdExpiresAt(LocalDateTime.now().minusMinutes(10))
                .build();
        bookingRepository.save(expiredHold);
        entityManager.flush();

        // Active hold on Nexon EV must count as overlap
        long nexonOverlap = vehicleRepository.countOverlappingVehicleBookings(nexonEv.getId(), start, end);
        assertEquals(1, nexonOverlap);

        // Expired hold on XUV700 must NOT count as overlap
        long xuvOverlap = vehicleRepository.countOverlappingVehicleBookings(xuv700.getId(), start, end);
        assertEquals(0, xuvOverlap);
    }

    @Test
    @DisplayName("findAvailableVehiclesForPeriod excludes vehicles with overlapping bookings")
    void testFindAvailableVehiclesForPeriod() {
        LocalDateTime start = LocalDateTime.now().plusDays(10);
        LocalDateTime end = LocalDateTime.now().plusDays(12);

        // Book City Hybrid for this period
        Booking booking = Booking.builder()
                .bookingReference("BK-TEST-CITY-001")
                .customer(testCustomer)
                .vehicle(cityHybrid)
                .startDateTime(start)
                .endDateTime(end)
                .pickupLocation("Bangalore Hub")
                .dropLocation("Bangalore Hub")
                .totalDays(2)
                .rentalAmount(BigDecimal.valueOf(4800.0))
                .totalAmount(BigDecimal.valueOf(5664.0))
                .bookingStatus(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        bookingRepository.save(booking);
        entityManager.flush();

        List<Vehicle> available = vehicleRepository.findAvailableVehiclesForPeriod(start, end);

        assertNotNull(available);
        assertEquals(2, available.size(), "City Hybrid is booked; Nexon EV and XUV700 remain available");

        List<String> regNumbers = available.stream().map(Vehicle::getRegistrationNumber).toList();
        assertTrue(regNumbers.contains("KA-01-EV-2024"));
        assertTrue(regNumbers.contains("KA-03-MB-7001"));
        assertFalse(regNumbers.contains("KA-02-HC-1122"), "Booked vehicle must be excluded");
        assertFalse(regNumbers.contains("KA-05-SW-9999"), "Inactive vehicle must be excluded");
        assertFalse(regNumbers.contains("KA-04-HY-3344"), "Maintenance vehicle must be excluded");
    }

    @Test
    @DisplayName("filterVehicles enforces all optional criteria and availability simultaneously")
    void testFilterVehiclesWithMultiCriteria() {
        // Filter by SUV type
        List<Vehicle> suvList = vehicleRepository.filterVehicles(
                VehicleType.SUV, null, null, null, null, null, null, null);
        assertEquals(1, suvList.size());
        assertEquals("KA-03-MB-7001", suvList.get(0).getRegistrationNumber());

        // Filter by FuelType ELECTRIC
        List<Vehicle> electricList = vehicleRepository.filterVehicles(
                null, FuelType.ELECTRIC, null, null, null, null, null, null);
        assertEquals(1, electricList.size());
        assertEquals("KA-01-EV-2024", electricList.get(0).getRegistrationNumber());

        // Filter by minSeats 6 (only XUV700 has 7 seats)
        List<Vehicle> min6Seats = vehicleRepository.filterVehicles(
                null, null, null, 6, null, null, null, null);
        assertEquals(1, min6Seats.size());
        assertEquals("KA-03-MB-7001", min6Seats.get(0).getRegistrationNumber());

        // Filter by maxPrice 2500 (City Hybrid is 2400)
        List<Vehicle> max2500 = vehicleRepository.filterVehicles(
                null, null, null, null, BigDecimal.valueOf(2500.0), null, null, null);
        assertEquals(1, max2500.size());
        assertEquals("KA-02-HC-1122", max2500.get(0).getRegistrationNumber());

        // Filter by brand "tata" (case-insensitive substring)
        List<Vehicle> tataList = vehicleRepository.filterVehicles(
                null, null, null, null, null, "tata", null, null);
        assertEquals(1, tataList.size());
        assertEquals("KA-01-EV-2024", tataList.get(0).getRegistrationNumber());
    }

    @Test
    @DisplayName("findByIdWithLock successfully returns vehicle with pessimistic lock")
    void testFindByIdWithLock() {
        Optional<Vehicle> lockedVehicle = vehicleRepository.findByIdWithLock(nexonEv.getId());
        assertTrue(lockedVehicle.isPresent());
        assertEquals("KA-01-EV-2024", lockedVehicle.get().getRegistrationNumber());
        assertEquals(VehicleStatus.AVAILABLE, lockedVehicle.get().getStatus());
        assertTrue(lockedVehicle.get().isAvailable());
    }
}

