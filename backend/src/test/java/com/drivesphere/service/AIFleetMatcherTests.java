package com.drivesphere.service;

import com.drivesphere.dto.AiRecommendationRequest;
import com.drivesphere.dto.AiRecommendationResponse;
import com.drivesphere.dto.VehicleRecommendationDto;
import com.drivesphere.entity.*;
import com.drivesphere.integration.gemini.GeminiClient;
import com.drivesphere.integration.maps.MapsClient;
import com.drivesphere.integration.weather.WeatherClient;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Rigorous automated tests for AI Fleet Matcher correctness (Priority 1):
 * 1. Operational inventory enforcement (no fallback to unavailable/maintenance via findAll)
 * 2. Rental period conflict detection
 * 3. Budget enforcement on total convoy cost
 * 4. Unique physical vehicle inventory (no duplicate counting of single units)
 * 5. Seating capacity satisfaction without misleading claims
 * 6. NO_FLEET_AVAILABLE status with clear diagnostic explanation
 */
class AIFleetMatcherTests {

    private GeminiClient geminiClient;
    private MapsClient mapsClient;
    private WeatherClient weatherClient;
    private VehicleRepository vehicleRepo;
    private EntityDtoMapper mapper;
    private AIService aiService;

    @BeforeEach
    void setUp() {
        geminiClient = Mockito.mock(GeminiClient.class);
        mapsClient = Mockito.mock(MapsClient.class);
        weatherClient = Mockito.mock(WeatherClient.class);
        vehicleRepo = Mockito.mock(VehicleRepository.class);
        mapper = new EntityDtoMapper();

        aiService = new AIService(geminiClient, mapsClient, weatherClient, vehicleRepo, mapper);
    }

    private Vehicle createVehicle(Long id, String model, int seats, BigDecimal dailyPrice, VehicleStatus status, boolean available) {
        Vehicle v = Vehicle.builder()
                .registrationNumber("KA-01-TEST-" + id)
                .brand("TestBrand")
                .model(model)
                .year(2024)
                .vehicleType(VehicleType.SEDAN)
                .fuelType(FuelType.PETROL)
                .transmission(Transmission.AUTOMATIC)
                .seatingCapacity(seats)
                .rentalPricePerDay(dailyPrice)
                .rentalPricePerHour(dailyPrice.divide(BigDecimal.valueOf(10), 2, java.math.RoundingMode.HALF_UP))
                .status(status)
                .available(available)
                .build();
        v.setId(id);
        return v;
    }

    @Test
    @DisplayName("P1.1: When operational fleet is empty, return NO_FLEET_AVAILABLE with explanation")
    void testNoOperationalInventory_returnsNoFleetAvailable() {
        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(Collections.emptyList());

        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(4)
                .maxBudgetPerDay(BigDecimal.valueOf(5000))
                .build());

        assertNotNull(response);
        assertEquals("NO_FLEET_AVAILABLE", response.getStatus());
        assertTrue(response.getRecommendations().isEmpty());
        assertTrue(response.getSummaryExplanation().contains("No operational vehicles are currently available"));
    }

    @Test
    @DisplayName("P1.2: Unavailable or maintenance vehicles must not be recommended via findAll() fallback")
    void testUnavailableVehicles_excludedFromRecommendations() {
        Vehicle maint = createVehicle(1L, "Maintenance Sedan", 5, BigDecimal.valueOf(2000), VehicleStatus.MAINTENANCE, false);
        Vehicle avail = createVehicle(2L, "Ready Sedan", 5, BigDecimal.valueOf(2500), VehicleStatus.AVAILABLE, true);

        // findOperationalAvailableVehicles only returns the available one
        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(List.of(avail));
        // Even if findAll() has maintenance vehicles, it should never be called
        when(vehicleRepo.findAll()).thenReturn(List.of(maint, avail));

        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(4)
                .maxBudgetPerDay(BigDecimal.valueOf(5000))
                .build());

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getRecommendations().size());
        assertEquals("Ready Sedan", response.getRecommendations().get(0).getVehicle().getModel());
    }

    @Test
    @DisplayName("P1.3: Rental period date conflicts filter out already booked vehicles")
    void testRentalPeriodDateConflict_filtersOutBookedVehicles() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(3);

        Vehicle freeVehicle = createVehicle(10L, "Free Electric", 5, BigDecimal.valueOf(3000), VehicleStatus.AVAILABLE, true);

        // When rental period is provided, AIService must query findAvailableVehiclesForPeriod
        when(vehicleRepo.findAvailableVehiclesForPeriod(start, end)).thenReturn(List.of(freeVehicle));

        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(4)
                .startDateTime(start)
                .endDateTime(end)
                .maxBudgetPerDay(BigDecimal.valueOf(4000))
                .build());

        assertNotNull(response);
        assertEquals(1, response.getRecommendations().size());
        assertEquals(10L, response.getRecommendations().get(0).getVehicle().getId());
    }

    @Test
    @DisplayName("P1.4: Insufficient budget returns NO_FLEET_AVAILABLE")
    void testInsufficientBudget_returnsNoFleetAvailable() {
        Vehicle luxury = createVehicle(1L, "Luxury Limousine", 5, BigDecimal.valueOf(10000), VehicleStatus.AVAILABLE, true);
        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(List.of(luxury));

        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(4)
                .maxBudgetPerDay(BigDecimal.valueOf(3000)) // Budget is 3000, vehicle is 10000
                .build());

        assertNotNull(response);
        assertEquals("NO_FLEET_AVAILABLE", response.getStatus());
        assertTrue(response.getRecommendations().isEmpty());
        assertTrue(response.getSummaryExplanation().contains("budget"));
    }

    @Test
    @DisplayName("P1.5: Multi-vehicle convoy must never duplicate a single physical vehicle unit")
    void testMultiVehicleConvoy_neverDuplicatesPhysicalUnits() {
        // Only 2 physical 4-seat vehicles exist in database
        Vehicle v1 = createVehicle(1L, "Compact 1", 4, BigDecimal.valueOf(1500), VehicleStatus.AVAILABLE, true);
        Vehicle v2 = createVehicle(2L, "Compact 2", 4, BigDecimal.valueOf(1500), VehicleStatus.AVAILABLE, true);

        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(List.of(v1, v2));

        // Request 12 passengers: requires at least 3 vehicles of 4 seats, but only 2 exist!
        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(12)
                .maxBudgetPerDay(BigDecimal.valueOf(10000))
                .build());

        assertNotNull(response);
        // Because only 2 physical units exist (total seats = 8 < 12), no combo can satisfy 12 passengers!
        assertEquals("NO_FLEET_AVAILABLE", response.getStatus());
        assertTrue(response.getRecommendations().isEmpty());
    }

    @Test
    @DisplayName("P1.6: Multi-vehicle convoy valid combination contains distinct units and verifies total capacity")
    void testMultiVehicleConvoy_validDistinctUnitsMeetCapacity() {
        // 3 physical 7-seat SUVs available
        Vehicle suv1 = createVehicle(101L, "Scorpio SUV A", 7, BigDecimal.valueOf(3000), VehicleStatus.AVAILABLE, true);
        suv1.setVehicleType(VehicleType.SUV);
        Vehicle suv2 = createVehicle(102L, "Scorpio SUV B", 7, BigDecimal.valueOf(3000), VehicleStatus.AVAILABLE, true);
        suv2.setVehicleType(VehicleType.SUV);
        Vehicle suv3 = createVehicle(103L, "Scorpio SUV C", 7, BigDecimal.valueOf(3000), VehicleStatus.AVAILABLE, true);
        suv3.setVehicleType(VehicleType.SUV);

        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(List.of(suv1, suv2, suv3));

        // Request 14 passengers: 2 SUVs of 7 seats = 14 seats
        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(14)
                .maxBudgetPerDay(BigDecimal.valueOf(8000))
                .build());

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.isMultiVehicleRequired());
        assertFalse(response.getRecommendations().isEmpty());

        for (VehicleRecommendationDto combo : response.getRecommendations()) {
            // Verify capacity >= requested passengers
            assertTrue(combo.getTotalSeatsCapacity() >= 14, "Total capacity must be at least 14");
            // Verify total budget constraint
            assertTrue(combo.getTotalDailyRental().compareTo(BigDecimal.valueOf(8000)) <= 0,
                    "Total convoy daily rental must not exceed maxBudgetPerDay");
            assertTrue(combo.isMultiVehicle());
            assertTrue(combo.getVehicleCount() >= 2);
        }
    }

    @Test
    @DisplayName("P1.7: Single vehicle recommendation calculates explainable match percentage")
    void testSingleVehicle_explainableMatchPercentage() {
        Vehicle ev = createVehicle(1L, "Tata Nexon EV", 5, BigDecimal.valueOf(2800), VehicleStatus.AVAILABLE, true);
        ev.setVehicleType(VehicleType.ELECTRIC);
        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(List.of(ev));

        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(4)
                .fuelPreference(FuelType.ELECTRIC)
                .maxBudgetPerDay(BigDecimal.valueOf(3500))
                .build());

        assertNotNull(response);
        assertEquals(1, response.getRecommendations().size());
        int score = response.getRecommendations().get(0).getMatchPercentage();
        assertNotNull(response.getRecommendations().get(0).getMatchReason());
        assertFalse(response.getRecommendations().get(0).getMatchReason().isBlank());
        assertTrue(response.getRecommendations().get(0).getMatchReason().toLowerCase().contains("electric")
                || response.getRecommendations().get(0).getMatchReason().toLowerCase().contains("emissions"));
    }
}
