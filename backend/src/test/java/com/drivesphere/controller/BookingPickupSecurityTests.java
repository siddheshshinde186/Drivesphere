package com.drivesphere.controller;

import com.drivesphere.dto.BookingDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.entity.RoleType;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.BookingService;
import com.drivesphere.service.RentalService;
import com.drivesphere.service.InvoiceService;
import com.drivesphere.service.TripTrackingService;
import com.drivesphere.dto.TripDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BookingPickupSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private RentalService rentalService;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private TripTrackingService tripService;

    @Test
    @DisplayName("1. Authenticated customer starts their own eligible booking -> 200 OK")
    @WithMockUser(username = "shinde.sid1866@gmail.com", roles = {"CUSTOMER"})
    void testCustomerStartsOwnEligibleBooking() throws Exception {
        UserDto userDto = UserDto.builder()
                .id(101L)
                .email("shinde.sid1866@gmail.com")
                .roles(Set.of(RoleType.ROLE_CUSTOMER))
                .build();
        when(authService.getCurrentUser()).thenReturn(userDto);
        when(rentalService.pickupVehicle(eq(28L), any(), eq(101L), eq(false)))
                .thenReturn(BookingDto.builder().id(28L).build());

        mockMvc.perform(post("/api/bookings/28/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("2. Customer attempts to start another customer's booking -> 403 Forbidden")
    @WithMockUser(username = "shinde.sid1866@gmail.com", roles = {"CUSTOMER"})
    void testCustomerStartsAnotherCustomerBookingForbidden() throws Exception {
        UserDto userDto = UserDto.builder()
                .id(101L)
                .email("shinde.sid1866@gmail.com")
                .roles(Set.of(RoleType.ROLE_CUSTOMER))
                .build();
        when(authService.getCurrentUser()).thenReturn(userDto);
        when(rentalService.pickupVehicle(eq(99L), any(), eq(101L), eq(false)))
                .thenThrow(new AccessDeniedException("Unauthorized: You do not own this booking."));

        mockMvc.perform(post("/api/bookings/99/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Unauthorized: You do not own this booking."));
    }

    @Test
    @DisplayName("3. Customer attempts to start future booking before window -> 409 Conflict")
    @WithMockUser(username = "shinde.sid1866@gmail.com", roles = {"CUSTOMER"})
    void testCustomerStartsFutureBookingRejected() throws Exception {
        UserDto userDto = UserDto.builder()
                .id(101L)
                .email("shinde.sid1866@gmail.com")
                .roles(Set.of(RoleType.ROLE_CUSTOMER))
                .build();
        when(authService.getCurrentUser()).thenReturn(userDto);
        when(rentalService.pickupVehicle(eq(28L), any(), eq(101L), eq(false)))
                .thenThrow(new IllegalStateException("Booking pickup is scheduled for future date. Trip cannot be started until permitted pickup window."));

        mockMvc.perform(post("/api/bookings/28/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Booking pickup is scheduled for future date. Trip cannot be started until permitted pickup window."));
    }

    @Test
    @DisplayName("4. Customer returns their own active booking -> 200 OK")
    @WithMockUser(username = "shinde.sid1866@gmail.com", roles = {"CUSTOMER"})
    void testCustomerReturnsOwnActiveBooking() throws Exception {
        UserDto userDto = UserDto.builder()
                .id(101L)
                .email("shinde.sid1866@gmail.com")
                .roles(Set.of(RoleType.ROLE_CUSTOMER))
                .build();
        when(authService.getCurrentUser()).thenReturn(userDto);
        when(rentalService.returnVehicle(eq(28L), any(), eq(101L), eq(false)))
                .thenReturn(BookingDto.builder().id(28L).build());

        mockMvc.perform(post("/api/bookings/28/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("5. Customer attempts to return another customer's booking -> 403 Forbidden")
    @WithMockUser(username = "shinde.sid1866@gmail.com", roles = {"CUSTOMER"})
    void testCustomerReturnsAnotherCustomerBookingForbidden() throws Exception {
        UserDto userDto = UserDto.builder()
                .id(101L)
                .email("shinde.sid1866@gmail.com")
                .roles(Set.of(RoleType.ROLE_CUSTOMER))
                .build();
        when(authService.getCurrentUser()).thenReturn(userDto);
        when(rentalService.returnVehicle(eq(99L), any(), eq(101L), eq(false)))
                .thenThrow(new AccessDeniedException("Unauthorized: You do not own this booking."));

        mockMvc.perform(post("/api/bookings/99/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Unauthorized: You do not own this booking."));
    }

    @Test
    @DisplayName("6. Customer simulates their own active trip -> 200 OK")
    @WithMockUser(username = "shinde.sid1866@gmail.com", roles = {"CUSTOMER"})
    void testCustomerSimulatesOwnTrip() throws Exception {
        UserDto userDto = UserDto.builder()
                .id(101L)
                .email("shinde.sid1866@gmail.com")
                .roles(Set.of(RoleType.ROLE_CUSTOMER))
                .build();
        when(authService.getCurrentUser()).thenReturn(userDto);
        when(tripService.simulateTripStep(eq(28L), eq(101L), eq(false)))
                .thenReturn(TripDto.builder().id(28L).isSimulatedTelemetry(true).build());

        mockMvc.perform(post("/api/trips/28/simulate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("7. Customer simulates another customer's trip -> 403 Forbidden")
    @WithMockUser(username = "shinde.sid1866@gmail.com", roles = {"CUSTOMER"})
    void testCustomerSimulatesAnotherCustomerTripForbidden() throws Exception {
        UserDto userDto = UserDto.builder()
                .id(101L)
                .email("shinde.sid1866@gmail.com")
                .roles(Set.of(RoleType.ROLE_CUSTOMER))
                .build();
        when(authService.getCurrentUser()).thenReturn(userDto);
        when(tripService.simulateTripStep(eq(99L), eq(101L), eq(false)))
                .thenThrow(new AccessDeniedException("Unauthorized to simulate this trip."));

        mockMvc.perform(post("/api/trips/99/simulate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Unauthorized to simulate this trip."));
    }
}
