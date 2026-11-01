package com.drivesphere.service;

import com.drivesphere.dto.BookingCreateRequest;
import com.drivesphere.dto.BookingDto;
import com.drivesphere.dto.PublicVehicleDto;
import com.drivesphere.dto.VehicleDto;
import com.drivesphere.entity.*;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class VehicleFilteringAndAvailabilityTests {

    private VehicleRepository vehicleRepo;
    private VehicleService vehicleService;
    private BookingRepository bookingRepo;
    private DriverRepository driverRepo;
    private UserRepository userRepo;
    private TripRepository tripRepo;
    private BookingService bookingService;
    private EntityDtoMapper mapper;

    private List<Vehicle> allVehicles;
    private User testCustomer;

    @BeforeEach
    void setUp() {
        vehicleRepo = Mockito.mock(VehicleRepository.class);
        bookingRepo = Mockito.mock(BookingRepository.class);
        driverRepo = Mockito.mock(DriverRepository.class);
        userRepo = Mockito.mock(UserRepository.class);
        tripRepo = Mockito.mock(TripRepository.class);
        mapper = new EntityDtoMapper();

        vehicleService = new VehicleService(vehicleRepo, null, null, mapper);
        bookingService = new BookingService(bookingRepo, vehicleRepo, driverRepo, userRepo, tripRepo, null, null, null, mapper);

        testCustomer = User.builder()
                .email("customer@example.com")
                .firstName("Aarav")
                .lastName("Sharma")
                .phoneNumber("9876543210")
                .active(true)
                .build();
        testCustomer.setId(10L);

        allVehicles = new ArrayList<>();

        // 1. Operational Available Tata Nexon EV
        Vehicle v1 = Vehicle.builder()
                .registrationNumber("KA-01-EV-2024").brand("Tata").model("Nexon EV Empowered")
                .vehicleType(VehicleType.ELECTRIC).fuelType(FuelType.ELECTRIC).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).rentalPricePerDay(BigDecimal.valueOf(2800.0)).rentalPricePerHour(BigDecimal.valueOf(200.0))
                .status(VehicleStatus.AVAILABLE).available(true).build();
        v1.setId(1L);
        allVehicles.add(v1);

        // 2. Operational Available Mahindra XUV700 (Diesel, Automatic, 7 seats, 3600/day)
        Vehicle v2 = Vehicle.builder()
                .registrationNumber("KA-03-MB-7001").brand("Mahindra").model("XUV700 AX7")
                .vehicleType(VehicleType.SUV).fuelType(FuelType.DIESEL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(7).rentalPricePerDay(BigDecimal.valueOf(3600.0)).rentalPricePerHour(BigDecimal.valueOf(250.0))
                .status(VehicleStatus.AVAILABLE).available(true).build();
        v2.setId(2L);
        allVehicles.add(v2);

        // 3. Operational Available Honda City (Petrol, Manual, 5 seats, 2200/day)
        Vehicle v3 = Vehicle.builder()
                .registrationNumber("KA-02-HC-1122").brand("Honda").model("City V-MT")
                .vehicleType(VehicleType.SEDAN).fuelType(FuelType.PETROL).transmission(Transmission.MANUAL)
                .seatingCapacity(5).rentalPricePerDay(BigDecimal.valueOf(2200.0)).rentalPricePerHour(BigDecimal.valueOf(180.0))
                .status(VehicleStatus.AVAILABLE).available(true).build();
        v3.setId(3L);
        allVehicles.add(v3);

        // 4. Inactive Vehicle (available = true, status = INACTIVE) - MUST BE EXCLUDED
        Vehicle v4 = Vehicle.builder()
                .registrationNumber("KA-05-IN-9999").brand("Maruti").model("Swift VXi")
                .vehicleType(VehicleType.HATCHBACK).fuelType(FuelType.PETROL).transmission(Transmission.MANUAL)
                .seatingCapacity(5).rentalPricePerDay(BigDecimal.valueOf(1500.0)).rentalPricePerHour(BigDecimal.valueOf(120.0))
                .status(VehicleStatus.INACTIVE).available(true).build();
        v4.setId(4L);
        allVehicles.add(v4);

        // 5. Maintenance Vehicle (available = false, status = MAINTENANCE) - MUST BE EXCLUDED
        Vehicle v5 = Vehicle.builder()
                .registrationNumber("KA-04-MN-5555").brand("Toyota").model("Innova Crysta")
                .vehicleType(VehicleType.SUV).fuelType(FuelType.DIESEL).transmission(Transmission.MANUAL)
                .seatingCapacity(7).rentalPricePerDay(BigDecimal.valueOf(3800.0)).rentalPricePerHour(BigDecimal.valueOf(260.0))
                .status(VehicleStatus.MAINTENANCE).available(false).build();
        v5.setId(5L);
        allVehicles.add(v5);

        // 6. Booked Vehicle (available = true, status = BOOKED) - MUST BE EXCLUDED
        Vehicle v6 = Vehicle.builder()
                .registrationNumber("KA-06-BK-4444").brand("Hyundai").model("Creta SX")
                .vehicleType(VehicleType.SUV).fuelType(FuelType.PETROL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).rentalPricePerDay(BigDecimal.valueOf(2900.0)).rentalPricePerHour(BigDecimal.valueOf(210.0))
                .status(VehicleStatus.BOOKED).available(true).build();
        v6.setId(6L);
        allVehicles.add(v6);

        // 7. Unavailable Vehicle (available = false, status = AVAILABLE) - MUST BE EXCLUDED
        Vehicle v7 = Vehicle.builder()
                .registrationNumber("KA-07-UN-3333").brand("Tata").model("Harrier XZA")
                .vehicleType(VehicleType.SUV).fuelType(FuelType.DIESEL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).rentalPricePerDay(BigDecimal.valueOf(3200.0)).rentalPricePerHour(BigDecimal.valueOf(230.0))
                .status(VehicleStatus.AVAILABLE).available(false).build();
        v7.setId(7L);
        allVehicles.add(v7);

        // Mock vehicleRepo.filterVehicles implementation faithfully matching the updated JPQL query:
        // WHERE v.available = true AND v.status = 'AVAILABLE'
        // AND (:vehicleType IS NULL OR v.vehicleType = :vehicleType)
        // AND (:fuelType IS NULL OR v.fuelType = :fuelType)
        // AND (:transmission IS NULL OR v.transmission = :transmission)
        // AND (:minSeats IS NULL OR v.seatingCapacity >= :minSeats)
        // AND (:maxPrice IS NULL OR v.rentalPricePerDay <= :maxPrice)
        // AND (:brand IS NULL OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :brand, '%')))
        when(vehicleRepo.filterVehicles(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    VehicleType type = invocation.getArgument(0);
                    FuelType fuel = invocation.getArgument(1);
                    Transmission trans = invocation.getArgument(2);
                    Integer minSeats = invocation.getArgument(3);
                    BigDecimal maxPrice = invocation.getArgument(4);
                    String brand = invocation.getArgument(5);
                    LocalDateTime start = invocation.getArgument(6);
                    LocalDateTime end = invocation.getArgument(7);

                    return allVehicles.stream()
                            .filter(v -> v.isAvailable() && v.getStatus() == VehicleStatus.AVAILABLE)
                            .filter(v -> type == null || v.getVehicleType() == type)
                            .filter(v -> fuel == null || v.getFuelType() == fuel)
                            .filter(v -> trans == null || v.getTransmission() == trans)
                            .filter(v -> minSeats == null || (v.getSeatingCapacity() != null && v.getSeatingCapacity() >= minSeats))
                            .filter(v -> maxPrice == null || (v.getRentalPricePerDay() != null && v.getRentalPricePerDay().compareTo(maxPrice) <= 0))
                            .filter(v -> brand == null || (v.getBrand() != null && v.getBrand().toLowerCase().contains(brand.toLowerCase())))
                            // If dates overlap with v1's booked period (e.g. tomorrow to day after tomorrow)
                            .filter(v -> !(start != null && end != null && v.getId().equals(1L) && start.isBefore(LocalDateTime.now().plusDays(2))))
                            .collect(Collectors.toList());
                });


        // Mock vehicleRepo.findOperationalAvailableVehicles()
        when(vehicleRepo.findOperationalAvailableVehicles()).thenAnswer(invocation ->
                allVehicles.stream()
                        .filter(v -> v.isAvailable() && v.getStatus() == VehicleStatus.AVAILABLE)
                        .collect(Collectors.toList())
        );
        when(vehicleRepo.findCustomerVisibleFleet()).thenAnswer(invocation ->
                allVehicles.stream()
                        .filter(v -> v.getStatus() != VehicleStatus.INACTIVE)
                        .collect(Collectors.toList())
        );

        when(userRepo.findById(10L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepo.findById(1L)).thenReturn(Optional.of(v1));
        when(vehicleRepo.findById(2L)).thenReturn(Optional.of(v2));
        when(vehicleRepo.findById(3L)).thenReturn(Optional.of(v3));
        when(vehicleRepo.findById(4L)).thenReturn(Optional.of(v4));
        when(vehicleRepo.findById(5L)).thenReturn(Optional.of(v5));
        when(vehicleRepo.findById(7L)).thenReturn(Optional.of(v7));

        when(bookingRepo.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(999L);
            return b;
        });

        when(tripRepo.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(888L);
            return t;
        });
    }

    @Test
    @DisplayName("1. Available vehicles appear in filtered results")
    void testAvailableVehiclesAppearInFilteredResults() {
        List<PublicVehicleDto> results = vehicleService.filterVehicles(VehicleType.ELECTRIC, null, null, null, null, null);
        assertEquals(1, results.size());
        assertEquals("Tata", results.get(0).getBrand());
        assertEquals("Nexon EV Empowered", results.get(0).getModel());
        assertTrue(results.get(0).isAvailable());
        assertEquals(VehicleStatus.AVAILABLE, results.get(0).getStatus());
    }

    @Test
    @DisplayName("2. Inactive, maintenance, booked, and unavailable vehicles do not appear")
    void testNonOperationalVehiclesExcludedFromFilteredResults() {
        List<PublicVehicleDto> results = vehicleService.filterVehicles(null, null, null, null, null, null);
        assertEquals(3, results.size());

        for (PublicVehicleDto v : results) {
            assertTrue(v.isAvailable(), "All filtered vehicles must have available=true");
            assertEquals(VehicleStatus.AVAILABLE, v.getStatus(), "All filtered vehicles must have status=AVAILABLE");
            assertNotEquals("Swift VXi", v.getModel(), "Inactive vehicle Swift VXi must not appear");
            assertNotEquals("Innova Crysta", v.getModel(), "Maintenance vehicle Innova Crysta must not appear");
            assertNotEquals("Creta SX", v.getModel(), "Booked vehicle Creta SX must not appear");
            assertNotEquals("Harrier XZA", v.getModel(), "Unavailable vehicle Harrier XZA must not appear");
        }
    }

    @Test
    @DisplayName("3. Every individual filter works correctly")
    void testEveryIndividualFilterWorks() {
        // Vehicle Category / Type filter
        List<PublicVehicleDto> evs = vehicleService.filterVehicles(VehicleType.ELECTRIC, null, null, null, null, null);
        assertEquals(1, evs.size());
        assertEquals(VehicleType.ELECTRIC, evs.get(0).getVehicleType());

        // Fuel Type filter
        List<PublicVehicleDto> diesel = vehicleService.filterVehicles(null, FuelType.DIESEL, null, null, null, null);
        assertEquals(1, diesel.size());
        assertEquals(FuelType.DIESEL, diesel.get(0).getFuelType());

        // Transmission filter
        List<PublicVehicleDto> manuals = vehicleService.filterVehicles(null, null, Transmission.MANUAL, null, null, null);
        assertEquals(1, manuals.size());
        assertEquals(Transmission.MANUAL, manuals.get(0).getTransmission());

        // Min Seats filter
        List<PublicVehicleDto> sevenSeaters = vehicleService.filterVehicles(null, null, null, 7, null, null);
        assertEquals(1, sevenSeaters.size());
        assertEquals(7, sevenSeaters.get(0).getSeatingCapacity());

        // Max Price filter
        List<PublicVehicleDto> budgetVehicles = vehicleService.filterVehicles(null, null, null, null, BigDecimal.valueOf(2500.0), null);
        assertEquals(1, budgetVehicles.size());
        assertEquals("Honda", budgetVehicles.get(0).getBrand());

        // Brand filter
        List<PublicVehicleDto> tataVehicles = vehicleService.filterVehicles(null, null, null, null, null, "Tata");
        assertEquals(1, tataVehicles.size());
        assertEquals("Tata", tataVehicles.get(0).getBrand());
    }

    @Test
    @DisplayName("4. Multiple filters combine using AND logic")
    void testMultipleFiltersCombineWithAndLogic() {
        // SUV AND DIESEL AND AUTOMATIC AND MIN SEATS 7 AND MAX PRICE 4000
        List<PublicVehicleDto> results = vehicleService.filterVehicles(
                VehicleType.SUV, FuelType.DIESEL, Transmission.AUTOMATIC, 7, BigDecimal.valueOf(4000.0), "Mahindra"
        );
        assertEquals(1, results.size());
        assertEquals("XUV700 AX7", results.get(0).getModel());

        // Same criteria but price lowered below rate (3000 < 3600) -> returns empty list (AND logic)
        List<PublicVehicleDto> noMatch = vehicleService.filterVehicles(
                VehicleType.SUV, FuelType.DIESEL, Transmission.AUTOMATIC, 7, BigDecimal.valueOf(3000.0), "Mahindra"
        );
        assertTrue(noMatch.isEmpty(), "Must return empty when one criteria of AND logic does not match");
    }

    @Test
    @DisplayName("5. An empty filter returns only operationally available vehicles")
    void testEmptyFilterReturnsOnlyOperationalAvailableVehicles() {
        List<PublicVehicleDto> results = vehicleService.filterVehicles(null, null, null, null, null, null);
        assertEquals(3, results.size());
        List<PublicVehicleDto> operationalFleet = vehicleService.getAvailableVehicles();
        assertEquals(operationalFleet.size(), results.size(), "Empty filter and operational fleet must match exactly");
    }

    @Test
    @DisplayName("6. Filtered vehicles can open the correct booking modal with valid DTO data")
    void testFilteredVehiclesContainCompleteBookingModalData() {
        List<PublicVehicleDto> results = vehicleService.filterVehicles(VehicleType.ELECTRIC, null, null, null, null, null);
        assertFalse(results.isEmpty());
        PublicVehicleDto v = results.get(0);

        assertNotNull(v.getId(), "Vehicle ID is required for openBookingModal");
        assertNotNull(v.getDisplayName(), "Display name is required for booking modal header");
        assertNotNull(v.getRentalPricePerDay(), "Rental price per day is required for modal price calculation");
        assertNotNull(v.getSeatingCapacity(), "Seating capacity is required for modal info badge");
        assertNotNull(v.getFuelType(), "Fuel type is required for modal info badge");
        assertTrue(v.getRentalPricePerDay().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("7. Clearing filters restores the full operational fleet")
    void testClearingFiltersRestoresOperationalFleet() {
        // 1. Apply restrictive filter
        List<PublicVehicleDto> filtered = vehicleService.filterVehicles(VehicleType.ELECTRIC, null, null, null, null, null);
        assertEquals(1, filtered.size());

        // 2. Clear filters (all nulls or empty brand)
        List<PublicVehicleDto> restored = vehicleService.filterVehicles(null, null, null, null, null, "   ");
        assertEquals(3, restored.size());
        assertEquals(vehicleService.getAvailableVehicles().size(), restored.size());
    }

    @Test
    @DisplayName("8. The backend rejects booking an unavailable vehicle")
    void testBackendRejectsBookingUnavailableVehicles() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(3);

        BigDecimal pLat = BigDecimal.valueOf(12.9716);
        BigDecimal pLng = BigDecimal.valueOf(77.5946);
        BigDecimal dLat = BigDecimal.valueOf(12.2958);
        BigDecimal dLng = BigDecimal.valueOf(76.6394);

        // Case A: Vehicle marked available = false (ID 7)
        BookingCreateRequest reqUnavailable = BookingCreateRequest.builder()
                .vehicleId(7L).startDateTime(start).endDateTime(end)
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();

        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(10L, reqUnavailable)
        );
        assertTrue(ex1.getMessage().contains("not available for booking"));

        // Case B: Vehicle marked status = INACTIVE (ID 4)
        BookingCreateRequest reqInactive = BookingCreateRequest.builder()
                .vehicleId(4L).startDateTime(start).endDateTime(end)
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(10L, reqInactive)
        );
        assertTrue(ex2.getMessage().contains("not available for booking"));

        // Case C: Vehicle marked status = MAINTENANCE (ID 5)
        BookingCreateRequest reqMaint = BookingCreateRequest.builder()
                .vehicleId(5L).startDateTime(start).endDateTime(end)
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();

        IllegalStateException ex3 = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(10L, reqMaint)
        );
        assertTrue(ex3.getMessage().contains("under maintenance"));

        // Case D: Valid Operational Available Vehicle (ID 1) -> Succeeds!
        BookingCreateRequest reqValid = BookingCreateRequest.builder()
                .vehicleId(1L).startDateTime(start).endDateTime(end)
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();

        BookingDto created = bookingService.createBooking(10L, reqValid);
        assertNotNull(created);
        assertEquals(BookingStatus.HELD, created.getBookingStatus());
    }

    @Test
    @DisplayName("9. Date-overlapping bookings are excluded from date-specific results")
    void testDateOverlappingBookingsExcluded() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(2);

        // General filter without dates: includes Vehicle 1 (Nexon EV), 2, and 3
        List<PublicVehicleDto> generalResults = vehicleService.filterVehicles(null, null, null, null, null, null);
        assertEquals(3, generalResults.size());

        // Date-specific filter: Vehicle 1 is booked for this period, so only vehicles 2 and 3 should appear
        List<PublicVehicleDto> dateResults = vehicleService.filterVehicles(null, null, null, null, null, null, start, end);
        assertEquals(2, dateResults.size());
        assertTrue(dateResults.stream().noneMatch(v -> v.getId().equals(1L)), "Vehicle 1 with overlapping booking must be excluded");
    }

    @Test
    @DisplayName("10. Overlapping booking attempts are rejected across all boundary scenarios")
    void testOverlappingBookingIsRejected() {
        // Vehicle 2 (Mahindra XUV700) has existing booking: 10 Nov 10:00 to 15 Nov 10:00
        LocalDateTime existingStart = LocalDateTime.of(2026, 11, 10, 10, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2026, 11, 15, 10, 0);

        BigDecimal pLat = BigDecimal.valueOf(12.9716);
        BigDecimal pLng = BigDecimal.valueOf(77.5946);
        BigDecimal dLat = BigDecimal.valueOf(12.2958);
        BigDecimal dLng = BigDecimal.valueOf(76.6394);

        // Mock overlap count based on: existingStart < requestedEnd AND existingEnd > requestedStart
        when(vehicleRepo.countOverlappingVehicleBookings(eq(2L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    LocalDateTime reqStart = inv.getArgument(1);
                    LocalDateTime reqEnd = inv.getArgument(2);
                    boolean overlaps = existingStart.isBefore(reqEnd) && existingEnd.isAfter(reqStart);
                    return overlaps ? 1L : 0L;
                });

        // Scenario A: Exact overlap (10 Nov to 15 Nov)
        BookingCreateRequest reqExact = BookingCreateRequest.builder()
                .vehicleId(2L).startDateTime(existingStart).endDateTime(existingEnd)
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();
        IllegalStateException exA = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(10L, reqExact)
        );
        assertTrue(exA.getMessage().contains("already booked for the specified dates"));

        // Scenario B: Inside existing booking (12 Nov to 14 Nov)
        BookingCreateRequest reqInside = BookingCreateRequest.builder()
                .vehicleId(2L)
                .startDateTime(LocalDateTime.of(2026, 11, 12, 10, 0))
                .endDateTime(LocalDateTime.of(2026, 11, 14, 10, 0))
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();
        IllegalStateException exB = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(10L, reqInside)
        );
        assertTrue(exB.getMessage().contains("already booked for the specified dates"));

        // Scenario C: Enclosing existing booking (8 Nov to 18 Nov)
        BookingCreateRequest reqEnclosing = BookingCreateRequest.builder()
                .vehicleId(2L)
                .startDateTime(LocalDateTime.of(2026, 11, 8, 10, 0))
                .endDateTime(LocalDateTime.of(2026, 11, 18, 10, 0))
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();
        IllegalStateException exC = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(10L, reqEnclosing)
        );
        assertTrue(exC.getMessage().contains("already booked for the specified dates"));
    }

    @Test
    @DisplayName("11. Non-overlapping bookings are accepted (5-8 Nov, 16-20 Nov, 20-25 Nov)")
    void testNonOverlappingBookingIsAccepted() {
        // Vehicle 2 (Mahindra XUV700) has existing booking: 10 Nov 10:00 to 15 Nov 10:00
        LocalDateTime existingStart = LocalDateTime.of(2026, 11, 10, 10, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2026, 11, 15, 10, 0);

        BigDecimal pLat = BigDecimal.valueOf(12.9716);
        BigDecimal pLng = BigDecimal.valueOf(77.5946);
        BigDecimal dLat = BigDecimal.valueOf(12.2958);
        BigDecimal dLng = BigDecimal.valueOf(76.6394);

        when(vehicleRepo.countOverlappingVehicleBookings(eq(2L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    LocalDateTime reqStart = inv.getArgument(1);
                    LocalDateTime reqEnd = inv.getArgument(2);
                    boolean overlaps = existingStart.isBefore(reqEnd) && existingEnd.isAfter(reqStart);
                    return overlaps ? 1L : 0L;
                });

        // Case 1: Before existing booking (5 Nov to 8 Nov)
        BookingCreateRequest reqBefore = BookingCreateRequest.builder()
                .vehicleId(2L)
                .startDateTime(LocalDateTime.of(2026, 11, 5, 10, 0))
                .endDateTime(LocalDateTime.of(2026, 11, 8, 10, 0))
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();
        BookingDto resBefore = bookingService.createBooking(10L, reqBefore);
        assertNotNull(resBefore);
        assertEquals(BookingStatus.HELD, resBefore.getBookingStatus());

        // Case 2: After existing booking (16 Nov to 20 Nov)
        BookingCreateRequest reqAfter = BookingCreateRequest.builder()
                .vehicleId(2L)
                .startDateTime(LocalDateTime.of(2026, 11, 16, 10, 0))
                .endDateTime(LocalDateTime.of(2026, 11, 20, 10, 0))
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();
        BookingDto resAfter = bookingService.createBooking(10L, reqAfter);
        assertNotNull(resAfter);
        assertEquals(BookingStatus.HELD, resAfter.getBookingStatus());

        // Case 3: Far future (20 Nov to 25 Nov)
        BookingCreateRequest reqFarFuture = BookingCreateRequest.builder()
                .vehicleId(2L)
                .startDateTime(LocalDateTime.of(2026, 11, 20, 10, 0))
                .endDateTime(LocalDateTime.of(2026, 11, 25, 10, 0))
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();
        BookingDto resFarFuture = bookingService.createBooking(10L, reqFarFuture);
        assertNotNull(resFarFuture);
        assertEquals(BookingStatus.HELD, resFarFuture.getBookingStatus());

        // Case 4: Back-to-back adjacent boundary booking (15 Nov 10:00 to 18 Nov 10:00)
        BookingCreateRequest reqAdjacent = BookingCreateRequest.builder()
                .vehicleId(2L)
                .startDateTime(existingEnd)
                .endDateTime(existingEnd.plusDays(3))
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();
        BookingDto resAdjacent = bookingService.createBooking(10L, reqAdjacent);
        assertNotNull(resAdjacent);
        assertEquals(BookingStatus.HELD, resAdjacent.getBookingStatus());
    }

    @Test
    @DisplayName("12. Cancelled booking releases dates immediately and does not block new bookings")
    void testCancelledBookingDoesNotBlockDates() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 15, 10, 0);

        // Cancelled booking for 10-15 Nov does not count as overlap
        when(vehicleRepo.countOverlappingVehicleBookings(eq(3L), eq(start), eq(end)))
                .thenReturn(0L);

        BigDecimal pLat = BigDecimal.valueOf(12.9716);
        BigDecimal pLng = BigDecimal.valueOf(77.5946);
        BigDecimal dLat = BigDecimal.valueOf(12.2958);
        BigDecimal dLng = BigDecimal.valueOf(76.6394);

        BookingCreateRequest req = BookingCreateRequest.builder()
                .vehicleId(3L).startDateTime(start).endDateTime(end)
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();

        BookingDto result = bookingService.createBooking(10L, req);
        assertNotNull(result);
        assertEquals(BookingStatus.HELD, result.getBookingStatus());
    }

    @Test
    @DisplayName("13. Overdue service restriction is strictly enforced at booking submission")
    void testOverdueServiceRestrictionIsEnforced() {
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(4);

        MaintenanceService maintServiceMock = Mockito.mock(MaintenanceService.class);
        when(maintServiceMock.calculateServiceStatus(any(Vehicle.class)))
                .thenReturn(com.drivesphere.entity.VehicleServiceStatus.OVERDUE);

        bookingService.setMaintenanceService(maintServiceMock);

        BigDecimal pLat = BigDecimal.valueOf(12.9716);
        BigDecimal pLng = BigDecimal.valueOf(77.5946);
        BigDecimal dLat = BigDecimal.valueOf(12.2958);
        BigDecimal dLng = BigDecimal.valueOf(76.6394);

        BookingCreateRequest req = BookingCreateRequest.builder()
                .vehicleId(1L).startDateTime(start).endDateTime(end)
                .pickupLocation("Bangalore Hub").dropLocation("Mysore Hub")
                .pickupLatitude(pLat).pickupLongitude(pLng).dropLatitude(dLat).dropLongitude(dLng)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                bookingService.createBooking(10L, req)
        );
        assertTrue(ex.getMessage().contains("overdue maintenance"));
    }

    @Test
    @DisplayName("14. Customer-visible fleet returns all registered non-inactive vehicles with operational status")
    void testCustomerVisibleFleetReturnsAllNonInactiveVehicles() {
        List<PublicVehicleDto> fleet = vehicleService.getCustomerVisibleFleet();
        assertNotNull(fleet);
        assertEquals(6, fleet.size(), "Must return all 6 non-inactive vehicles");
        assertTrue(fleet.stream().anyMatch(v -> v.getStatus() == VehicleStatus.AVAILABLE));
        assertTrue(fleet.stream().anyMatch(v -> v.getStatus() == VehicleStatus.BOOKED));
        assertTrue(fleet.stream().anyMatch(v -> v.getStatus() == VehicleStatus.MAINTENANCE));
        assertTrue(fleet.stream().noneMatch(v -> v.getStatus() == VehicleStatus.INACTIVE));
    }
}
