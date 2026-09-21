package com.drivesphere.service;

import com.drivesphere.dto.*;
import com.drivesphere.entity.*;
import com.drivesphere.integration.gemini.GeminiClient;
import com.drivesphere.integration.maps.MapsClient;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.integration.weather.WeatherClient;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.DriverTrustScoreRepository;
import com.drivesphere.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BusinessLogicTests {

    private VehicleRepository vehicleRepo;
    private AIService aiService;
    private MapsClient mapsClient;

    @BeforeEach
    void setUp() {
        GeminiClient gemini = new GeminiClient();
        mapsClient = new MapsClient();
        WeatherClient weather = new WeatherClient();
        vehicleRepo = Mockito.mock(VehicleRepository.class);
        EntityDtoMapper mapper = new EntityDtoMapper();

        Vehicle nexon = Vehicle.builder().registrationNumber("KA-01-EV-2024").brand("Tata").model("Nexon EV Empowered")
                .year(2024).vehicleType(VehicleType.ELECTRIC).fuelType(FuelType.ELECTRIC).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(6.5)).rentalPricePerDay(BigDecimal.valueOf(2800.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        nexon.setId(1L);

        Vehicle ioniq = Vehicle.builder().registrationNumber("KA-05-EV-4096").brand("Hyundai").model("Ioniq 5 EV")
                .year(2024).vehicleType(VehicleType.ELECTRIC).fuelType(FuelType.ELECTRIC).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(6.8)).rentalPricePerDay(BigDecimal.valueOf(5200.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        ioniq.setId(2L);

        Vehicle xuv700 = Vehicle.builder().registrationNumber("KA-03-MB-7001").brand("Mahindra").model("XUV700 AX7 Luxury")
                .year(2023).vehicleType(VehicleType.SUV).fuelType(FuelType.DIESEL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(7).mileage(BigDecimal.valueOf(14.8)).rentalPricePerDay(BigDecimal.valueOf(3600.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        xuv700.setId(3L);

        Vehicle fortuner = Vehicle.builder().registrationNumber("KA-04-TR-8800").brand("Toyota").model("Fortuner Legender 4x4")
                .year(2024).vehicleType(VehicleType.SUV).fuelType(FuelType.DIESEL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(7).mileage(BigDecimal.valueOf(12.5)).rentalPricePerDay(BigDecimal.valueOf(4800.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        fortuner.setId(4L);

        Vehicle city = Vehicle.builder().registrationNumber("KA-02-HC-1122").brand("Honda").model("City ZX e:HEV Hybrid")
                .year(2023).vehicleType(VehicleType.SEDAN).fuelType(FuelType.HYBRID).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(27.1)).rentalPricePerDay(BigDecimal.valueOf(2400.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        city.setId(5L);

        Vehicle bmw = Vehicle.builder().registrationNumber("KA-01-BM-3300").brand("BMW").model("330Li M Sport Gran Limousine")
                .year(2024).vehicleType(VehicleType.LUXURY).fuelType(FuelType.PETROL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(15.3)).rentalPricePerDay(BigDecimal.valueOf(8500.0)).status(VehicleStatus.AVAILABLE).available(true).build();
        bmw.setId(6L);

        List<Vehicle> fullFleet = List.of(nexon, ioniq, xuv700, fortuner, city, bmw);
        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(fullFleet);
        when(vehicleRepo.findAll()).thenReturn(fullFleet);
        when(vehicleRepo.findById(1L)).thenReturn(Optional.of(nexon));
        when(vehicleRepo.findById(3L)).thenReturn(Optional.of(xuv700));

        aiService = new AIService(gemini, mapsClient, weather, vehicleRepo, mapper);
    }

    @Test
    void case1_passengers1_shouldRecommendValidSingleVehicle() {
        AiRecommendationResponse res = aiService.recommendVehicles(AiRecommendationRequest.builder().passengers(1).build());
        assertNotNull(res);
        assertFalse(res.getRecommendations().isEmpty());
        assertTrue(res.getRecommendations().get(0).getTotalSeatsCapacity() >= 1);
        assertEquals(1, res.getRecommendations().get(0).getVehicleCount());
        assertFalse(res.isMultiVehicleRequired());
    }

    @Test
    void case2_passengers5_shouldRecommendValid5SeatVehicle() {
        AiRecommendationResponse res = aiService.recommendVehicles(AiRecommendationRequest.builder().passengers(5).build());
        assertNotNull(res);
        assertFalse(res.getRecommendations().isEmpty());
        VehicleRecommendationDto topPick = res.getRecommendations().get(0);
        assertTrue(topPick.getTotalSeatsCapacity() >= 5);
        assertEquals(1, topPick.getVehicleCount());
    }

    @Test
    void case3_passengers6_shouldNeverRecommendSingle5SeatVehicle() {
        AiRecommendationResponse res = aiService.recommendVehicles(AiRecommendationRequest.builder().passengers(6).build());
        assertNotNull(res);
        assertFalse(res.getRecommendations().isEmpty());

        for (VehicleRecommendationDto r : res.getRecommendations()) {
            assertTrue(r.getTotalSeatsCapacity() >= 6, "Total capacity must be >= 6");
            if (r.getVehicleCount() == 1) {
                assertTrue(r.getVehicle().getSeatingCapacity() >= 6, "A single vehicle must have at least 6 seats");
            }
        }
    }

    @Test
    void case4_passengers7_shouldRecommend7SeatVehicle() {
        AiRecommendationResponse res = aiService.recommendVehicles(AiRecommendationRequest.builder().passengers(7).build());
        assertNotNull(res);
        assertFalse(res.getRecommendations().isEmpty());
        VehicleRecommendationDto top = res.getRecommendations().get(0);
        assertTrue(top.getTotalSeatsCapacity() >= 7);
    }

    @Test
    void case5_passengers10_shouldRecommendMultiVehicleFleet() {
        AiRecommendationResponse res = aiService.recommendVehicles(AiRecommendationRequest.builder().passengers(10).build());
        assertNotNull(res);
        assertTrue(res.isMultiVehicleRequired());
        assertFalse(res.getRecommendations().isEmpty());

        for (VehicleRecommendationDto r : res.getRecommendations()) {
            assertTrue(r.getTotalSeatsCapacity() >= 10, "Total seats must meet or exceed 10");
            assertTrue(r.getVehicleCount() >= 2, "Must recommend at least 2 vehicles for 10 passengers");
        }
    }

    @Test
    void case6_passengers15_mustNeverRecommendSingleVehicle() {
        AiRecommendationResponse res = aiService.recommendVehicles(AiRecommendationRequest.builder().passengers(15).build());
        assertNotNull(res);
        assertTrue(res.isMultiVehicleRequired());
        assertFalse(res.getRecommendations().isEmpty());

        // HARD ASSERTION: Top pick must NEVER be a single 5-seater Tata Nexon!
        VehicleRecommendationDto top = res.getRecommendations().get(0);
        assertTrue(top.getVehicleCount() >= 3, "At least 3 vehicles needed for 15 passengers (max single is 7 seats)");
        assertTrue(top.getTotalSeatsCapacity() >= 15, "Total capacity must be >= 15");
        assertTrue(top.isMultiVehicle());
        assertFalse(res.getSummaryExplanation().contains("Your top fleet choice is the Tata Nexon EV"));
        assertTrue(res.getSummaryExplanation().contains("15 passengers cannot fit in one single vehicle"));
    }

    @Test
    void case7_passengersGreaterThanFleetCapacity_shouldReturnClearNoFleetMessage() {
        AiRecommendationResponse res = aiService.recommendVehicles(AiRecommendationRequest.builder().passengers(50).build());
        assertNotNull(res);
        assertEquals("NO_FLEET_AVAILABLE", res.getStatus());
        assertTrue(res.getRecommendations().isEmpty(), "No vehicles should be recommended when requested > total capacity");
        assertTrue(res.getSummaryExplanation().contains("No single vehicle or multi-vehicle combination can accommodate 50 passengers"));
    }

    @Test
    void case8_bangaloreToMysore_shouldReturnRealisticRouteAndDistance() {
        TripPlannerResponse plan = aiService.planTrip(TripPlannerRequest.builder()
                .source("Bangalore")
                .destination("Mysore")
                .vehicleId(1L) // Tata Nexon EV
                .build());

        assertNotNull(plan);
        assertTrue(plan.getDistanceKm().doubleValue() >= 130.0 && plan.getDistanceKm().doubleValue() <= 160.0,
                "Distance between Bangalore and Mysore should be ~140-150 km, got: " + plan.getDistanceKm());
        assertTrue(plan.getDurationMinutes() >= 90 && plan.getDurationMinutes() <= 200,
                "Duration should be realistic travel time, got: " + plan.getDurationMinutes());
        assertTrue(plan.isElectric());
        assertEquals("kWh", plan.getEnergyUnit());
        assertNotNull(plan.getDestinationWeather());
        assertTrue(plan.getDestinationLocationName().contains("Mysur") || plan.getDestinationLocationName().contains("Mysore"));
    }

    @Test
    void case9_invalidLocation_shouldThrowCleanValidationException() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiService.planTrip(TripPlannerRequest.builder()
                    .source("Bangalore")
                    .destination("UnknownFakeLocationXYZ9999")
                    .build());
        });
    }

    @Test
    void driverTrustScoreCalculation_ShouldComputeAccurateLevelAndExplanation() {
        DriverTrustScoreRepository repo = Mockito.mock(DriverTrustScoreRepository.class);
        when(repo.findByDriver(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        DriverTrustScoreService service = new DriverTrustScoreService(repo);

        Driver driver = Driver.builder()
                .experienceYears(5)
                .rating(BigDecimal.valueOf(4.80))
                .totalTrips(35)
                .cancellationRate(BigDecimal.valueOf(2.0))
                .verification(DriverVerification.builder().status(VerificationStatus.APPROVED).build())
                .build();

        DriverTrustScore score = service.calculateAndSaveTrustScore(driver);

        assertNotNull(score);
        assertTrue(score.getOverallScore().doubleValue() >= 80.0, "Score should be >= 80 for verified experienced driver");
        assertTrue(score.getTrustLevel() == TrustLevel.GOOD || score.getTrustLevel() == TrustLevel.EXCELLENT);
    }

    @Test
    void razorpaySignatureValidation_ShouldVerifyExpectedMockHmac() {
        RazorpayClient client = new RazorpayClient();
        String orderId = "order_mock_test_12345";
        String paymentId = "pay_mock_test_67890";
        String validSignature = "mock_sig_12345";

        assertTrue(client.verifySignature(orderId, paymentId, validSignature));
    }
}
