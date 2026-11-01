package com.drivesphere.controller;

import com.drivesphere.entity.*;
import com.drivesphere.integration.cloudinary.CloudinaryClient;
import com.drivesphere.integration.gemini.GeminiClient;
import com.drivesphere.integration.maps.MapsClient;
import com.drivesphere.integration.razorpay.RazorpayClient;
import com.drivesphere.integration.weather.WeatherClient;
import com.drivesphere.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ExploreFleetEndpointLiveTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VehicleRepository vehicleRepository;

    @MockBean
    private GeminiClient geminiClient;

    @MockBean
    private MapsClient mapsClient;

    @MockBean
    private WeatherClient weatherClient;

    @MockBean
    private CloudinaryClient cloudinaryClient;

    @MockBean
    private RazorpayClient razorpayClient;

    @BeforeEach
    void seedFleetIfEmpty() {
        if (vehicleRepository.count() < 8) {
            for (int i = 1; i <= 8; i++) {
                Vehicle v = Vehicle.builder()
                        .brand("Brand" + i)
                        .model("Model" + i)
                        .registrationNumber("KA01TEST" + i)
                        .vin("VINTEST000000000" + i)
                        .year(2023)
                        .vehicleType(VehicleType.SUV)
                        .fuelType(FuelType.PETROL)
                        .transmission(Transmission.AUTOMATIC)
                        .seatingCapacity(5)
                        .mileage(new BigDecimal("18.5"))
                        .status(VehicleStatus.AVAILABLE)
                        .available(true)
                        .rentalPricePerDay(new BigDecimal("2000.00"))
                        .rentalPricePerHour(new BigDecimal("150.00"))
                        .build();
                vehicleRepository.save(v);
            }
        }
    }

    @Test
    @DisplayName("1. Verify GET /api/vehicles/fleet returns HTTP 200 with all registered customer-visible vehicles")
    void testGetVehiclesFleet() throws Exception {
        mockMvc.perform(get("/api/vehicles/fleet")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.data[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data[0].brand").isNotEmpty());
    }

    @Test
    @DisplayName("2. Verify GET /api/vehicles/explore returns HTTP 200 with all registered customer-visible vehicles")
    void testGetVehiclesExplore() throws Exception {
        mockMvc.perform(get("/api/vehicles/explore")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.data[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data[0].brand").isNotEmpty());
    }

    @Test
    @DisplayName("3. Verify GET /api/vehicles/available returns HTTP 200 with operational available vehicles")
    void testGetAvailableVehicles() throws Exception {
        mockMvc.perform(get("/api/vehicles/available")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
