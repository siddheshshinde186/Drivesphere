package com.drivesphere.service;

import com.drivesphere.dto.AiChatRequest;
import com.drivesphere.dto.AiChatResponse;
import com.drivesphere.dto.AiRecommendationRequest;
import com.drivesphere.dto.AiRecommendationResponse;
import com.drivesphere.entity.FuelType;
import com.drivesphere.entity.Transmission;
import com.drivesphere.entity.Vehicle;
import com.drivesphere.entity.VehicleStatus;
import com.drivesphere.entity.VehicleType;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AIServiceTests {

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

        Vehicle nexon = Vehicle.builder()
                .registrationNumber("KA-01-EV-2024")
                .brand("Tata")
                .model("Nexon EV Empowered")
                .year(2024)
                .vehicleType(VehicleType.ELECTRIC)
                .fuelType(FuelType.ELECTRIC)
                .transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5)
                .mileage(BigDecimal.valueOf(6.5))
                .rentalPricePerDay(BigDecimal.valueOf(2800.0))
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .build();
        nexon.setId(1L);

        when(vehicleRepo.findOperationalAvailableVehicles()).thenReturn(List.of(nexon));
        when(vehicleRepo.findAll()).thenReturn(List.of(nexon));
        when(geminiClient.getModelDisplayName()).thenReturn("Google Gemini 3.5 Flash Lite");

        aiService = new AIService(geminiClient, mapsClient, weatherClient, vehicleRepo, mapper);
    }

    @Test
    @DisplayName("Chat with successful Gemini response should report Gemini provider and false fallbackMode")
    void chat_withSuccessfulGemini_shouldReportGeminiProvider() {
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.executePrompt(anyString()))
                .thenReturn(GeminiClient.GeminiResult.success("Google Gemini says hello!"));

        AiChatResponse response = aiService.chat(AiChatRequest.builder()
                .message("Which car is best for family?")
                .build());

        assertNotNull(response);
        assertEquals("Google Gemini says hello!", response.getReply());
        assertFalse(response.isFallbackMode());
        assertEquals("Google Gemini 3.5 Flash Lite", response.getModelProvider());
    }

    @Test
    @DisplayName("Chat with unconfigured Gemini should report Offline Heuristic provider and true fallbackMode")
    void chat_withUnconfiguredGemini_shouldReportOfflineHeuristicProvider() {
        when(geminiClient.isConfigured()).thenReturn(false);

        AiChatResponse response = aiService.chat(AiChatRequest.builder()
                .message("Which car is best for family?")
                .build());

        assertNotNull(response);
        assertTrue(response.isFallbackMode());
        assertTrue(response.getModelProvider().contains("Offline Mode"));
        assertTrue(response.getReply().contains("family"));
    }

    @Test
    @DisplayName("Chat with failed Gemini call should report Gemini Fallback provider and true fallbackMode")
    void chat_withFailedGemini_shouldReportFallbackProvider() {
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.executePrompt(anyString()))
                .thenReturn(GeminiClient.GeminiResult.failure("HTTP 503 Service Unavailable"));

        AiChatResponse response = aiService.chat(AiChatRequest.builder()
                .message("Which car is best for family?")
                .build());

        assertNotNull(response);
        assertTrue(response.isFallbackMode());
        assertEquals("Heuristic Mobility Engine [Gemini Fallback]", response.getModelProvider());
        assertTrue(response.getReply().contains("family"));
    }

    @Test
    @DisplayName("recommendVehicles with successful Gemini should report generatedByGemini true")
    void recommendVehicles_withSuccessfulGemini_shouldReportGeneratedByGeminiTrue() {
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.executePrompt(anyString()))
                .thenReturn(GeminiClient.GeminiResult.success("Gemini generated summary: The Tata Nexon EV Empowered perfectly fits 4 passengers."));

        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(4)
                .build());

        assertNotNull(response);
        assertTrue(response.isGeneratedByGemini());
        assertTrue(response.getSummaryExplanation().contains("Gemini generated summary"));
    }

    @Test
    @DisplayName("recommendVehicles with failed Gemini should fallback to deterministic explanation with generatedByGemini false")
    void recommendVehicles_withFailedGemini_shouldReportGeneratedByGeminiFalse() {
        when(geminiClient.isConfigured()).thenReturn(true);
        when(geminiClient.executePrompt(anyString()))
                .thenReturn(GeminiClient.GeminiResult.failure("HTTP 429 Quota Exceeded"));

        AiRecommendationResponse response = aiService.recommendVehicles(AiRecommendationRequest.builder()
                .passengers(4)
                .build());

        assertNotNull(response);
        assertFalse(response.isGeneratedByGemini());
        assertTrue(response.getSummaryExplanation().contains("Tata Nexon EV Empowered"));
    }
}
