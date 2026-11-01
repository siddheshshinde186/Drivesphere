package com.drivesphere.config;

import com.drivesphere.entity.*;
import com.drivesphere.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.drivesphere.service.AuditLogService;
import com.drivesphere.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Initializes foundational platform configuration (roles, vehicle categories)
 * and optionally seeds development demo accounts when explicitly enabled via
 * drivesphere.demo-data.enabled=true (default: false).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Value("${drivesphere.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @Value("${drivesphere.admin.bootstrap-email:${ADMIN_BOOTSTRAP_EMAIL:}}")
    private String bootstrapAdminEmail;

    @Value("${drivesphere.admin.bootstrap-password:${ADMIN_BOOTSTRAP_PASSWORD:}}")
    private String bootstrapAdminPassword;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final VehicleCategoryRepository categoryRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final DriverVerificationRepository verificationRepository;
    private final DriverTrustScoreRepository trustScoreRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Seed Roles if not present (Foundational)
        Role adminRole;
        Role fleetRole;
        Role driverRole;
        Role customerRole;

        if (roleRepository.count() == 0) {
            log.info("Seeding foundational platform roles...");
            adminRole = roleRepository.save(Role.builder().name(RoleType.ROLE_ADMIN).description("Platform Administrator").build());
            fleetRole = roleRepository.save(Role.builder().name(RoleType.ROLE_FLEET_MANAGER).description("Fleet Operations Manager").build());
            driverRole = roleRepository.save(Role.builder().name(RoleType.ROLE_DRIVER).description("Professional Verified Driver").build());
            customerRole = roleRepository.save(Role.builder().name(RoleType.ROLE_CUSTOMER).description("Mobility Customer").build());
        } else {
            adminRole = roleRepository.findByName(RoleType.ROLE_ADMIN).orElse(null);
            fleetRole = roleRepository.findByName(RoleType.ROLE_FLEET_MANAGER).orElse(null);
            driverRole = roleRepository.findByName(RoleType.ROLE_DRIVER).orElse(null);
            customerRole = roleRepository.findByName(RoleType.ROLE_CUSTOMER).orElse(null);
        }

        // 2. Seed Categories if not present (Foundational)
        if (categoryRepository.count() == 0) {
            log.info("Seeding foundational vehicle categories...");
            categoryRepository.save(VehicleCategory.builder()
                    .name("Electric Mobility").slug("electric-mobility")
                    .description("Zero-emission high-efficiency electric vehicles with smart telemetry")
                    .baseRatePerKm(BigDecimal.valueOf(14.0)).baseFare(BigDecimal.valueOf(60.0)).build());

            categoryRepository.save(VehicleCategory.builder()
                    .name("Executive Sedan").slug("executive-sedan")
                    .description("Premium comfortable sedans for business commutes and highway travel")
                    .baseRatePerKm(BigDecimal.valueOf(16.0)).baseFare(BigDecimal.valueOf(80.0)).build());

            categoryRepository.save(VehicleCategory.builder()
                    .name("Adventure SUV").slug("adventure-suv")
                    .description("Spacious 7-seater and rugged all-wheel drive SUVs for families and expeditions")
                    .baseRatePerKm(BigDecimal.valueOf(22.0)).baseFare(BigDecimal.valueOf(120.0)).build());

            categoryRepository.save(VehicleCategory.builder()
                    .name("Luxury Elite").slug("luxury-elite")
                    .description("Top-tier luxury vehicles with prestige cabin appointments and VIP styling")
                    .baseRatePerKm(BigDecimal.valueOf(38.0)).baseFare(BigDecimal.valueOf(250.0)).build());

            categoryRepository.save(VehicleCategory.builder()
                    .name("Urban Hatchback").slug("urban-hatchback")
                    .description("Compact, fuel-sipping vehicles engineered for nimble city navigation")
                    .baseRatePerKm(BigDecimal.valueOf(11.0)).baseFare(BigDecimal.valueOf(40.0)).build());

            categoryRepository.save(VehicleCategory.builder()
                    .name("Commercial Logistics & Shuttle").slug("commercial-van")
                    .description("High-payload vans and passenger transit shuttles for corporate groups")
                    .baseRatePerKm(BigDecimal.valueOf(20.0)).baseFare(BigDecimal.valueOf(150.0)).build());
        }

        // 3. Demo Data Guard (Production safety)
        if (!demoDataEnabled) {
            log.info("Production mode active: drivesphere.demo-data.enabled=false. Demo users, vehicles, and bookings will NOT be seeded.");
            provisionInitialAdminIfRequested(adminRole);
            synchronizeVehicleRatings();
            return;
        }

        if (userRepository.count() > 0) {
            log.info("User accounts already exist in database. Skipping demo data creation.");
            return;
        }

        log.info("Initializing development demo accounts and sample fleet data...");

        // 3. Seed Demo Users
        String hash = passwordEncoder.encode("Password@123");

        User adminUser = userRepository.save(User.builder()
                .email("admin@drivesphere.local").passwordHash(hash)
                .firstName("System").lastName("Administrator")
                .phoneNumber("+91 9876543210").avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80")
                .active(true).verified(true).roles(new HashSet<>(Set.of(adminRole))).build());

        User fleetUser = userRepository.save(User.builder()
                .email("fleet@drivesphere.local").passwordHash(hash)
                .firstName("Vikram").lastName("Mehta")
                .phoneNumber("+91 9876543211").avatarUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80")
                .active(true).verified(true).roles(new HashSet<>(Set.of(fleetRole))).build());

        User driverUser = userRepository.save(User.builder()
                .email("driver@drivesphere.local").passwordHash(hash)
                .firstName("Rajesh").lastName("Kumar")
                .phoneNumber("+91 9876543212").avatarUrl("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=200&q=80")
                .active(true).verified(true).roles(new HashSet<>(Set.of(driverRole))).build());

        User customerUser = userRepository.save(User.builder()
                .email("customer@drivesphere.local").passwordHash(hash)
                .firstName("Priya").lastName("Sharma")
                .phoneNumber("+91 9876543213").avatarUrl("https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80")
                .active(true).verified(true).roles(new HashSet<>(Set.of(customerRole))).build());

        // 4. Seed Driver Profile, Verification, and Trust Score
        Driver driver = driverRepository.save(Driver.builder()
                .user(driverUser).licenseNumber("DL-0420180019283")
                .licenseExpiryDate(LocalDate.of(2030, 5, 15))
                .experienceYears(7).rating(BigDecimal.valueOf(4.90))
                .totalTrips(84).cancellationRate(BigDecimal.valueOf(1.20))
                .available(true).dailyRate(BigDecimal.valueOf(950.0))
                .languages("English, Hindi, Kannada").address("124 Indiranagar, Bangalore")
                .profilePhotoUrl("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80")
                .build());

        verificationRepository.save(DriverVerification.builder()
                .driver(driver).status(VerificationStatus.APPROVED)
                .drivingSkillScore(94).verifiedBy(adminUser)
                .identityDocUrl("https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=600&q=80")
                .licenseDocUrl("https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=600&q=80")
                .policeVerificationDocUrl("https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=600&q=80")
                .adminNotes("Full background and government driving license verified.")
                .submittedAt(java.time.Instant.now()).verifiedAt(java.time.Instant.now())
                .build());

        trustScoreRepository.save(DriverTrustScore.builder()
                .driver(driver).overallScore(BigDecimal.valueOf(88.90))
                .trustLevel(TrustLevel.EXCELLENT)
                .verificationFactor(BigDecimal.valueOf(25.0))
                .experienceFactor(BigDecimal.valueOf(20.0))
                .ratingFactor(BigDecimal.valueOf(24.5))
                .completionFactor(BigDecimal.valueOf(20.0))
                .incidentPenalty(BigDecimal.valueOf(0.60))
                .explanation("Driver Trust Score: 88.9/100 (EXCELLENT). Breakdown: Document Verification (25.0/25), Professional Experience (20.0/20), Customer Feedback Rating (24.5/25), Trip Completion (20.0/20), Cancellation Deduction (-0.6). Note: Algorithmic academic reliability metric, not an official government license certification.")
                .lastCalculatedAt(java.time.Instant.now())
                .build());

        // 5. Seed Vehicles
        VehicleCategory catEv = categoryRepository.findBySlug("electric-mobility").orElse(null);
        VehicleCategory catSedan = categoryRepository.findBySlug("executive-sedan").orElse(null);
        VehicleCategory catSuv = categoryRepository.findBySlug("adventure-suv").orElse(null);
        VehicleCategory catLux = categoryRepository.findBySlug("luxury-elite").orElse(null);

        Vehicle v1 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-01-EV-2024").vin("VIN-TAT-NEXON-001")
                .brand("Tata").model("Nexon EV Empowered").year(2024)
                .vehicleType(VehicleType.ELECTRIC).fuelType(FuelType.ELECTRIC).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(312.0)).batteryCapacityKwh(BigDecimal.valueOf(40.5))
                .rentalPricePerDay(BigDecimal.valueOf(2800.0)).rentalPricePerHour(BigDecimal.valueOf(220.0))
                .description("Long-range electric compact SUV with ventilated seats and Level-1 ADAS safety.")
                .location("Koramangala Hub, Bangalore").latitude(BigDecimal.valueOf(12.9352)).longitude(BigDecimal.valueOf(77.6245))
                .status(VehicleStatus.AVAILABLE).available(true).currentOdometerKm(BigDecimal.valueOf(14200.0))
                .primaryImageUrl("https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80")
                .category(catEv).averageRating(BigDecimal.valueOf(4.90)).totalRatings(18).build());

        Vehicle v2 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-01-EV-2025").vin("VIN-HYU-IONIQ-002")
                .brand("Hyundai").model("Ioniq 5 EV").year(2024)
                .vehicleType(VehicleType.ELECTRIC).fuelType(FuelType.ELECTRIC).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(450.0)).batteryCapacityKwh(BigDecimal.valueOf(72.6))
                .rentalPricePerDay(BigDecimal.valueOf(5200.0)).rentalPricePerHour(BigDecimal.valueOf(420.0))
                .description("Futuristic ultra-fast charging electric crossover with augmented reality HUD.")
                .location("Indiranagar Hub, Bangalore").latitude(BigDecimal.valueOf(12.9784)).longitude(BigDecimal.valueOf(77.6408))
                .status(VehicleStatus.AVAILABLE).available(true).currentOdometerKm(BigDecimal.valueOf(8900.0))
                .primaryImageUrl("https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=800&q=80")
                .category(catEv).averageRating(BigDecimal.valueOf(4.95)).totalRatings(24).build());

        Vehicle v3 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-03-MG-4411").vin("VIN-MAH-XUV-003")
                .brand("Mahindra").model("XUV700 AX7 Luxury").year(2023)
                .vehicleType(VehicleType.SUV).fuelType(FuelType.DIESEL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(7).mileage(BigDecimal.valueOf(14.8)).fuelTankCapacityL(BigDecimal.valueOf(60.0))
                .rentalPricePerDay(BigDecimal.valueOf(3600.0)).rentalPricePerHour(BigDecimal.valueOf(290.0))
                .description("Commanding 7-seater premium SUV with panoramic skyroof and Sony 3D sound.")
                .location("Whitefield Station, Bangalore").latitude(BigDecimal.valueOf(12.9698)).longitude(BigDecimal.valueOf(77.7499))
                .status(VehicleStatus.AVAILABLE).available(true).currentOdometerKm(BigDecimal.valueOf(26400.0))
                .primaryImageUrl("https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=800&q=80")
                .category(catSuv).averageRating(BigDecimal.valueOf(4.85)).totalRatings(32).build());

        Vehicle v4 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-05-TR-9922").vin("VIN-TOY-FORT-004")
                .brand("Toyota").model("Fortuner Legender 4x4").year(2024)
                .vehicleType(VehicleType.SUV).fuelType(FuelType.DIESEL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(7).mileage(BigDecimal.valueOf(12.2)).fuelTankCapacityL(BigDecimal.valueOf(80.0))
                .rentalPricePerDay(BigDecimal.valueOf(4800.0)).rentalPricePerHour(BigDecimal.valueOf(380.0))
                .description("Rugged expedition-ready 4WD flagship with cat-eye LED headlights and high ground clearance.")
                .location("MG Road Terminal, Bangalore").latitude(BigDecimal.valueOf(12.9756)).longitude(BigDecimal.valueOf(77.6066))
                .status(VehicleStatus.AVAILABLE).available(true).currentOdometerKm(BigDecimal.valueOf(18300.0))
                .primaryImageUrl("https://images.unsplash.com/photo-1533473359331-0135ef1b58bf?auto=format&fit=crop&w=800&q=80")
                .category(catSuv).averageRating(BigDecimal.valueOf(4.90)).totalRatings(40).build());

        Vehicle v5 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-01-SD-3301").vin("VIN-HON-CITY-005")
                .brand("Honda").model("City ZX e:HEV Hybrid").year(2023)
                .vehicleType(VehicleType.SEDAN).fuelType(FuelType.HYBRID).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(27.1)).fuelTankCapacityL(BigDecimal.valueOf(40.0))
                .rentalPricePerDay(BigDecimal.valueOf(2400.0)).rentalPricePerHour(BigDecimal.valueOf(190.0))
                .description("Self-charging strong hybrid executive sedan delivering phenomenal fuel economy.")
                .location("Jayanagar Hub, Bangalore").latitude(BigDecimal.valueOf(12.9308)).longitude(BigDecimal.valueOf(77.5838))
                .status(VehicleStatus.AVAILABLE).available(true).currentOdometerKm(BigDecimal.valueOf(22100.0))
                .primaryImageUrl("https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=800&q=80")
                .category(catSedan).averageRating(BigDecimal.valueOf(4.80)).totalRatings(15).build());

        Vehicle v6 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-04-BM-8899").vin("VIN-BMW-3SER-007")
                .brand("BMW").model("330Li M Sport Gran Limousine").year(2024)
                .vehicleType(VehicleType.LUXURY).fuelType(FuelType.PETROL).transmission(Transmission.AUTOMATIC)
                .seatingCapacity(5).mileage(BigDecimal.valueOf(15.3)).fuelTankCapacityL(BigDecimal.valueOf(59.0))
                .rentalPricePerDay(BigDecimal.valueOf(8500.0)).rentalPricePerHour(BigDecimal.valueOf(680.0))
                .description("Executive extended-wheelbase sports saloon with Vernasca leather and BMW Curved Display.")
                .location("UB City Luxury Concierge, Bangalore").latitude(BigDecimal.valueOf(12.9716)).longitude(BigDecimal.valueOf(77.5957))
                .status(VehicleStatus.AVAILABLE).available(true).currentOdometerKm(BigDecimal.valueOf(9100.0))
                .primaryImageUrl("https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=800&q=80")
                .category(catLux).averageRating(BigDecimal.valueOf(4.98)).totalRatings(29).build());

        // 6. Sample Completed Booking, Payment, and Review
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingReference("DS-20260901-ABC123").customer(customerUser).vehicle(v1).driver(driver)
                .startDateTime(LocalDateTime.now().minusDays(2)).endDateTime(LocalDateTime.now().minusDays(1))
                .pickupLocation("Koramangala, Bangalore").dropLocation("Mysore Palace Road, Mysore")
                .pickupLatitude(BigDecimal.valueOf(12.9352)).pickupLongitude(BigDecimal.valueOf(77.6245))
                .dropLatitude(BigDecimal.valueOf(12.3051)).dropLongitude(BigDecimal.valueOf(76.6551))
                .totalDays(2).totalHours(36).rentalAmount(BigDecimal.valueOf(5600.0))
                .driverAmount(BigDecimal.valueOf(1900.0)).taxAmount(BigDecimal.valueOf(1350.0))
                .totalAmount(BigDecimal.valueOf(8850.0)).bookingStatus(BookingStatus.COMPLETED)
                .paymentStatus(PaymentStatus.PAID).pickupTime(LocalDateTime.now().minusDays(2))
                .returnTime(LocalDateTime.now().minusDays(1)).notes("Seamless weekend excursion.")
                .build());

        paymentRepository.save(Payment.builder()
                .booking(booking).amount(BigDecimal.valueOf(8850.0)).currency("INR")
                .gateway(PaymentGateway.MOCK).gatewayOrderId("order_mock_demo_001")
                .gatewayPaymentId("pay_mock_demo_001").status(PaymentStatus.PAID)
                .build());

        reviewRepository.save(Review.builder()
                .booking(booking).customer(customerUser).vehicle(v1).driver(driver)
                .rating(BigDecimal.valueOf(5.0)).vehicleRating(BigDecimal.valueOf(5.0))
                .driverRating(BigDecimal.valueOf(5.0))
                .comment("Remarkable experience with the Tata Nexon EV. Rajesh was an exceptionally polite and safe driver.")
                .build());

        maintenanceRepository.save(Maintenance.builder()
                .vehicle(v3).serviceType(ServiceType.ROUTINE)
                .serviceDate(LocalDate.now().minusDays(15)).nextServiceDate(LocalDate.now().plusMonths(3))
                .description("Regular 25,000 km periodic maintenance: engine oil change, oil filter, tire rotation.")
                .cost(BigDecimal.valueOf(4500.0)).status(MaintenanceStatus.COMPLETED)
                .odometerKm(BigDecimal.valueOf(25000.0)).notes("Vehicle inspected and certified in optimal condition.")
                .build());

        log.info("DriveSphere foundational seed data successfully populated!");
    }

    private void provisionInitialAdminIfRequested(Role adminRole) {
        if (bootstrapAdminEmail == null || bootstrapAdminEmail.isBlank()
                || bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
            log.info("No initial admin bootstrap credentials provided in environment. Skipping admin provisioning.");
            return;
        }

        String cleanEmail = bootstrapAdminEmail.trim().toLowerCase();
        boolean adminExists = userRepository.findAll().stream().anyMatch(u -> u.hasRole(RoleType.ROLE_ADMIN));
        if (adminExists) {
            log.info("An administrator account already exists in the database. Initial bootstrap skipped.");
            return;
        }

        if (bootstrapAdminPassword.trim().length() < 8) {
            log.error("CRITICAL: ADMIN_BOOTSTRAP_PASSWORD must be at least 8 characters. Admin provisioning aborted.");
            return;
        }

        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            log.warn("User with email {} already exists. Assigning ROLE_ADMIN...", EmailService.maskEmail(cleanEmail));
            User existing = userRepository.findByEmail(cleanEmail).get();
            if (adminRole != null) {
                existing.getRoles().add(adminRole);
            }
            existing.setActive(true);
            existing.setVerified(true);
            existing.setEmailVerified(true);
            userRepository.save(existing);
            if (auditLogService != null) {
                auditLogService.logAction("ADMIN_BOOTSTRAP_ROLE_ASSIGNED", "USER", existing.getId().toString(),
                        "Promoted existing user to Administrator via one-time environment bootstrap", "127.0.0.1");
            }
            return;
        }

        User adminUser = User.builder()
                .email(cleanEmail)
                .passwordHash(passwordEncoder.encode(bootstrapAdminPassword.trim()))
                .firstName("Platform")
                .lastName("Administrator")
                .phoneNumber("+91 9999999999")
                .active(true)
                .verified(true)
                .emailVerified(true)
                .roles(adminRole != null ? new HashSet<>(Set.of(adminRole)) : new HashSet<>())
                .build();

        User saved = userRepository.save(adminUser);
        log.info("Successfully provisioned initial Platform Administrator: {}", EmailService.maskEmail(cleanEmail));

        if (auditLogService != null) {
            auditLogService.logAction("ADMIN_BOOTSTRAP_CREATED", "USER", saved.getId().toString(),
                    "Initial platform administrator created via one-time secure environment bootstrap", "127.0.0.1");
        }
    }

    public void setBootstrapAdminEmail(String email) {
        this.bootstrapAdminEmail = email;
    }

    public void setBootstrapAdminPassword(String password) {
        this.bootstrapAdminPassword = password;
    }

    public void setDemoDataEnabled(boolean enabled) {
        this.demoDataEnabled = enabled;
    }

    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    private void synchronizeVehicleRatings() {
        if (reviewRepository.count() == 0) {
            log.info("No customer reviews in system; skipping vehicle rating synchronization.");
            return;
        }
        for (Vehicle vehicle : vehicleRepository.findAll()) {
            List<Review> vehicleReviews = reviewRepository.findByVehicleIdOrderByCreatedAtDesc(vehicle.getId());
            if (vehicleReviews.isEmpty()) {
                if (vehicle.getTotalRatings() != null && vehicle.getTotalRatings() > 0) {
                    vehicle.setAverageRating(null);
                    vehicle.setTotalRatings(0);
                    vehicleRepository.save(vehicle);
                }
            } else {
                double avg = vehicleReviews.stream()
                        .mapToDouble(r -> (r.getVehicleRating() != null ? r.getVehicleRating() : r.getRating()).doubleValue())
                        .average().orElse(0.0);
                vehicle.setAverageRating(BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));
                vehicle.setTotalRatings(vehicleReviews.size());
                vehicleRepository.save(vehicle);
            }
        }
    }
}
