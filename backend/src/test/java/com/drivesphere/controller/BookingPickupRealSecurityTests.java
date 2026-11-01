package com.drivesphere.controller;

import com.drivesphere.dto.RentalActionRequest;
import com.drivesphere.entity.*;
import com.drivesphere.repository.*;
import com.drivesphere.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BookingPickupRealSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private User customerA;
    private User customerB;
    private String tokenA;
    private String tokenB;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleType.ROLE_CUSTOMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_CUSTOMER, "Customer role")));

        customerA = userRepository.save(User.builder()
                .email("test.customera@drivesphere.com")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .firstName("Customer")
                .lastName("A")
                .roles(Set.of(customerRole))
                .active(true)
                .emailVerified(true)
                .build());

        customerB = userRepository.save(User.builder()
                .email("test.customerb@drivesphere.com")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .firstName("Customer")
                .lastName("B")
                .roles(Set.of(customerRole))
                .active(true)
                .emailVerified(true)
                .build());

        tokenA = jwtUtils.generateTokenFromUsername(customerA.getEmail());
        tokenB = jwtUtils.generateTokenFromUsername(customerB.getEmail());

        vehicle = vehicleRepository.save(Vehicle.builder()
                .brand("Hyundai")
                .model("Ioniq 5")
                .year(2024)
                .registrationNumber("KA-01-TEST-" + System.currentTimeMillis() % 10000)
                .vehicleType(VehicleType.SUV)
                .fuelType(FuelType.ELECTRIC)
                .transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5)
                .rentalPricePerDay(BigDecimal.valueOf(4500))
                .rentalPricePerHour(BigDecimal.valueOf(250))
                .status(VehicleStatus.AVAILABLE)
                .available(true)
                .mileage(BigDecimal.valueOf(18.5))
                .build());
    }

    private Booking createBooking(User customer, LocalDateTime start, LocalDateTime end, BookingStatus bStatus, PaymentStatus pStatus) {
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingReference("DS-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000))
                .customer(customer)
                .vehicle(vehicle)
                .startDateTime(start)
                .endDateTime(end)
                .pickupLocation("Bangalore Hub")
                .dropLocation("Mysore Hub")
                .totalDays(1)
                .rentalAmount(BigDecimal.valueOf(4500))
                .driverAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .depositAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(4500))
                .bookingStatus(bStatus)
                .paymentStatus(pStatus)
                .build());

        tripRepository.save(Trip.builder()
                .booking(booking)
                .startLatitude(BigDecimal.valueOf(12.9716))
                .startLongitude(BigDecimal.valueOf(77.5946))
                .destLatitude(BigDecimal.valueOf(12.2958))
                .destLongitude(BigDecimal.valueOf(76.6394))
                .currentLatitude(BigDecimal.valueOf(12.9716))
                .currentLongitude(BigDecimal.valueOf(77.5946))
                .status(TripStatus.NOT_STARTED)
                .build());

        return booking;
    }

    @Test
    @DisplayName("1. Real End-to-End: Customer starts own eligible paid booking -> 200 OK")
    void testCustomerStartsOwnEligibleBookingReal() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = createBooking(customerA, now.minusMinutes(30), now.plusDays(1), BookingStatus.CONFIRMED, PaymentStatus.PAID);

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/pickup")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookingStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("2. Real End-to-End: Customer attempts to start another customer's booking -> 403 Forbidden")
    void testCustomerStartsAnotherCustomerBookingReal() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = createBooking(customerA, now.minusMinutes(30), now.plusDays(1), BookingStatus.CONFIRMED, PaymentStatus.PAID);

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/pickup")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Unauthorized: You do not own this booking."));
    }

    @Test
    @DisplayName("3. Real End-to-End: Customer attempts to start future booking before window -> 409 Conflict")
    void testCustomerStartsFutureBookingReal() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // 5 days in the future (outside 2-hour window)
        Booking booking = createBooking(customerA, now.plusDays(5), now.plusDays(7), BookingStatus.CONFIRMED, PaymentStatus.PAID);

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/pickup")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("4. Real End-to-End: Customer returns own active booking -> 200 OK")
    void testCustomerReturnsOwnActiveBookingReal() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = createBooking(customerA, now.minusHours(2), now.plusDays(1), BookingStatus.ACTIVE, PaymentStatus.PAID);

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/return")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookingStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("5. Real End-to-End: Customer attempts to return another customer's booking -> 403 Forbidden")
    void testCustomerReturnsAnotherCustomerBookingReal() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = createBooking(customerA, now.minusHours(2), now.plusDays(1), BookingStatus.ACTIVE, PaymentStatus.PAID);

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/return")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Unauthorized: You do not own this booking."));
    }
}
