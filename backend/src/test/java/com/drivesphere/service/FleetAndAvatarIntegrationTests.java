package com.drivesphere.service;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.DriverDto;
import com.drivesphere.dto.PublicVehicleDto;
import com.drivesphere.dto.StoredDocumentDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.dto.VehicleDto;
import com.drivesphere.entity.*;
import com.drivesphere.integration.cloudinary.CloudinaryClient;
import com.drivesphere.mapper.EntityDtoMapper;
import com.drivesphere.repository.DriverRepository;
import com.drivesphere.repository.DriverVerificationRepository;
import com.drivesphere.repository.UserRepository;
import com.drivesphere.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FleetAndAvatarIntegrationTests {

    @TempDir
    Path tempStorageDir;

    private VehicleRepository vehicleRepository;
    private UserRepository userRepository;
    private DriverVerificationRepository verificationRepository;
    private CloudinaryClient cloudinaryClient;
    private DocumentStorageService documentStorageService;
    private UserService userService;
    private AuthService authService;
    private EntityDtoMapper mapper;
    private VehicleService vehicleService;

    private User testUser;
    private UserDto testUserDto;

    @BeforeEach
    void setUp() {
        vehicleRepository = mock(VehicleRepository.class);
        userRepository = mock(UserRepository.class);
        verificationRepository = mock(DriverVerificationRepository.class);
        cloudinaryClient = mock(CloudinaryClient.class);
        authService = mock(AuthService.class);
        mapper = new EntityDtoMapper();

        documentStorageService = new DocumentStorageService(cloudinaryClient, verificationRepository, userRepository);
        ReflectionTestUtils.setField(documentStorageService, "uploadDir", tempStorageDir.toAbsolutePath().toString());
        documentStorageService.init();

        userService = new UserService(userRepository, null, null, mapper);
        vehicleService = new VehicleService(vehicleRepository, null, null, mapper);

        testUser = User.builder()
                .firstName("Siddesh")
                .lastName("Shinde")
                .email("shinde.sid1866@gmail.com")
                .phoneNumber("+919833802478")
                .avatarUrl(null)
                .active(true)
                .verified(true)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_CUSTOMER).build()))
                .build();
        testUser.setId(12L);
        testUserDto = mapper.toUserDto(testUser);
    }

    @Test
    @DisplayName("1. Default Fleet returns all registered customer-visible vehicles (including BOOKED/RENTED)")
    void testDefaultFleet_returnsAllCustomerVisibleVehicles() {
        Vehicle v1 = Vehicle.builder().brand("Tata").model("Nexon EV").status(VehicleStatus.AVAILABLE).available(true).rentalPricePerDay(BigDecimal.valueOf(2500)).build();
        v1.setId(1L);
        Vehicle v2 = Vehicle.builder().brand("Hyundai").model("Ioniq 5").status(VehicleStatus.BOOKED).available(false).rentalPricePerDay(BigDecimal.valueOf(4500)).build();
        v2.setId(2L);
        Vehicle v3 = Vehicle.builder().brand("Mahindra").model("XUV700").status(VehicleStatus.RENTED).available(false).rentalPricePerDay(BigDecimal.valueOf(3500)).build();
        v3.setId(3L);

        when(vehicleRepository.findCustomerVisibleFleet()).thenReturn(List.of(v1, v2, v3));

        List<PublicVehicleDto> fleet = vehicleService.getCustomerVisibleFleet();
        assertNotNull(fleet);
        assertEquals(3, fleet.size(), "All 3 non-inactive vehicles must be visible to customers");
        assertEquals(VehicleStatus.AVAILABLE, fleet.get(0).getStatus());
        assertEquals(VehicleStatus.BOOKED, fleet.get(1).getStatus());
        assertEquals(VehicleStatus.RENTED, fleet.get(2).getStatus());
    }

    @Test
    @DisplayName("2. Filtered fleet without dates retains booked vehicles so customers can inspect & check dates")
    void testFilterFleet_withoutDates_retainsBookedVehicles() {
        Vehicle v1 = Vehicle.builder().brand("Tata").model("Nexon EV").status(VehicleStatus.AVAILABLE).vehicleType(VehicleType.SUV).available(true).build();
        v1.setId(1L);
        Vehicle v2 = Vehicle.builder().brand("Mahindra").model("XUV700").status(VehicleStatus.BOOKED).vehicleType(VehicleType.SUV).available(false).build();
        v2.setId(2L);

        when(vehicleRepository.filterVehicles(eq(VehicleType.SUV), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(v1, v2));

        List<PublicVehicleDto> filtered = vehicleService.filterVehicles(VehicleType.SUV, null, null, null, null, null, null, null);
        assertEquals(2, filtered.size(), "Filtering by SUV without dates must include both available and booked SUVs");
    }

    @Test
    @DisplayName("3. Filtered fleet with dates excludes vehicles that have overlapping bookings")
    void testFilterFleet_withDates_excludesOverlappingBookings() {
        Vehicle v1 = Vehicle.builder().brand("Tata").model("Nexon EV").status(VehicleStatus.AVAILABLE).vehicleType(VehicleType.SUV).available(true).build();
        v1.setId(1L);
        // Vehicle 2 has overlap, so repository returns only v1
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(5);

        when(vehicleRepository.filterVehicles(eq(VehicleType.SUV), isNull(), isNull(), isNull(), isNull(), isNull(), eq(start), eq(end)))
                .thenReturn(List.of(v1));

        List<PublicVehicleDto> filtered = vehicleService.filterVehicles(VehicleType.SUV, null, null, null, null, null, start, end);
        assertEquals(1, filtered.size());
        assertEquals("Tata", filtered.get(0).getBrand());
    }

    @Test
    @DisplayName("4. JPG Avatar Upload succeeds, writes physical file to storage, and generates public URL")
    void testAvatarUpload_validJpgFile_persistedAndAccessible() throws IOException {
        when(cloudinaryClient.isConfigured()).thenReturn(false);

        byte[] validJpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
        MockMultipartFile jpgFile = new MockMultipartFile("file", "profile.jpg", "image/jpeg", validJpgBytes);

        StoredDocumentDto stored = documentStorageService.storePublicFile(jpgFile, 12L);
        assertNotNull(stored);
        assertNotNull(stored.getFileUrl());
        assertTrue(stored.getFileUrl().startsWith("/api/documents/public/"), "Avatar URL must use public streaming path");

        // Verify physical file was written to disk
        String storedFilename = stored.getFilename();
        Resource loadedResource = documentStorageService.loadPublicFileAsResource(storedFilename);
        assertTrue(loadedResource.exists(), "Stored avatar file must physically exist on disk");
        assertTrue(loadedResource.isReadable(), "Stored avatar file must be readable");
    }

    @Test
    @DisplayName("5. PNG and WEBP Avatar Uploads succeed with correct magic bytes")
    void testAvatarUpload_validPngAndWebp_persisted() throws IOException {
        when(cloudinaryClient.isConfigured()).thenReturn(false);

        // PNG
        byte[] validPngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile pngFile = new MockMultipartFile("file", "avatar.png", "image/png", validPngBytes);
        StoredDocumentDto storedPng = documentStorageService.storePublicFile(pngFile, 12L);
        assertNotNull(storedPng);
        assertTrue(storedPng.getFileUrl().endsWith(".png"));

        // WEBP
        byte[] validWebpBytes = new byte[]{'R', 'I', 'F', 'F', 0x20, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
        MockMultipartFile webpFile = new MockMultipartFile("file", "avatar.webp", "image/webp", validWebpBytes);
        StoredDocumentDto storedWebp = documentStorageService.storePublicFile(webpFile, 12L);
        assertNotNull(storedWebp);
        assertTrue(storedWebp.getFileUrl().endsWith(".webp"));
    }

    @Test
    @DisplayName("6. Avatar upload rejects file exceeding 5MB limit")
    void testAvatarUpload_rejectsFileExceeding5MB() {
        byte[] oversizedBytes = new byte[6 * 1024 * 1024]; // 6 MB
        oversizedBytes[0] = (byte) 0xFF;
        oversizedBytes[1] = (byte) 0xD8;
        oversizedBytes[2] = (byte) 0xFF;

        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", oversizedBytes);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                documentStorageService.storePublicFile(largeFile, 12L)
        );
        assertTrue(ex.getMessage().contains("exceeds 5MB limit"));
    }

    @Test
    @DisplayName("7. Public Document streaming strictly blocks access to private Driver KYC documents")
    void testPublicDocumentEndpoint_blocksDriverKycDocuments() {
        DriverVerification mockKyc = mock(DriverVerification.class);
        when(mockKyc.getLicenseDocUrl()).thenReturn("/api/documents/download/private_driver_license.jpg");
        when(verificationRepository.findAll()).thenReturn(List.of(mockKyc));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                documentStorageService.loadPublicFileAsResource("private_driver_license.jpg")
        );
        assertTrue(ex.getMessage().contains("Private driver verification documents cannot be accessed"));
    }

    @Test
    @DisplayName("8. Public Document streaming strictly blocks non-image files")
    void testPublicDocumentEndpoint_blocksNonImageFiles() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                documentStorageService.loadPublicFileAsResource("statement.pdf")
        );
        assertTrue(ex.getMessage().contains("Only image assets are accessible"));
    }

    @Test
    @DisplayName("9. Cloudinary upload returns secure cloud URL when configured")
    void testCloudinaryUpload_whenConfigured_returnsCloudUrl() {
        when(cloudinaryClient.isConfigured()).thenReturn(true);
        when(cloudinaryClient.uploadBytes(any(), any(), any(), eq(false)))
                .thenReturn("https://res.cloudinary.com/drivesphere/image/upload/v12345/avatar.jpg");

        byte[] validJpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
        MockMultipartFile jpgFile = new MockMultipartFile("file", "profile.jpg", "image/jpeg", validJpgBytes);

        StoredDocumentDto stored = documentStorageService.storePublicFile(jpgFile, 12L);
        assertNotNull(stored);
        assertEquals("https://res.cloudinary.com/drivesphere/image/upload/v12345/avatar.jpg", stored.getFileUrl());
    }

    @Test
    @DisplayName("10. UserService updateAvatar persists avatar URL to user entity and database")
    void testUserService_updateAvatar_persistsInDatabase() {
        when(userRepository.findById(12L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto updated = userService.updateAvatar(12L, "/api/documents/public/new_avatar.jpg");
        assertNotNull(updated);
        assertEquals("/api/documents/public/new_avatar.jpg", updated.getAvatarUrl());
        assertEquals("/api/documents/public/new_avatar.jpg", testUser.getAvatarUrl());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("11. Vehicle image upload to Cloudinary updates primary vehicle image")
    void testVehicleImage_uploadToCloudinary_updatesPrimaryImageUrl() {
        when(cloudinaryClient.isConfigured()).thenReturn(true);
        when(cloudinaryClient.uploadBytes(any(), any(), any(), eq(false)))
                .thenReturn("https://res.cloudinary.com/akc1womd/image/upload/v12345/nexon.jpg");

        Vehicle v = Vehicle.builder().brand("Tata").model("Nexon EV").status(VehicleStatus.AVAILABLE).build();
        v.setId(1L);
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(v));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleDto updated = vehicleService.addVehicleImage(1L, "https://res.cloudinary.com/akc1womd/image/upload/v12345/nexon.jpg", true);
        assertNotNull(updated);
        assertEquals("https://res.cloudinary.com/akc1womd/image/upload/v12345/nexon.jpg", updated.getPrimaryImageUrl());
        assertEquals("https://res.cloudinary.com/akc1womd/image/upload/v12345/nexon.jpg", v.getPrimaryImageUrl());
    }

    @Test
    @DisplayName("12. Driver profile photo upload updates driver profile photo URL")
    void testDriverProfilePhoto_uploadToCloudinary_updatesProfilePhotoUrl() {
        DriverRepository driverRepository = mock(DriverRepository.class);
        DriverService driverService = new DriverService(driverRepository, mapper);

        Driver driver = Driver.builder()
                .user(testUser)
                .dailyRate(BigDecimal.valueOf(800))
                .available(true)
                .build();
        driver.setId(5L);

        when(driverRepository.findById(5L)).thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        DriverDto updated = driverService.updateProfilePhoto(5L, "https://res.cloudinary.com/akc1womd/image/upload/v12345/driver.jpg", 12L, false);
        assertNotNull(updated);
        assertEquals("https://res.cloudinary.com/akc1womd/image/upload/v12345/driver.jpg", updated.getProfilePhotoUrl());
        assertEquals("https://res.cloudinary.com/akc1womd/image/upload/v12345/driver.jpg", driver.getProfilePhotoUrl());
    }

    @Test
    @DisplayName("13. Driver KYC verification documents are uploaded privately with download route")
    void testDriverKycDocuments_alwaysPrivate_neverUsesPublicEndpoint() {
        when(cloudinaryClient.isConfigured()).thenReturn(true);
        when(cloudinaryClient.uploadBytes(any(), any(), any(), eq(true)))
                .thenReturn("https://res.cloudinary.com/akc1womd/image/authenticated/s--xyz--/license.jpg");

        byte[] validJpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
        MockMultipartFile licenseDoc = new MockMultipartFile("file", "license.jpg", "image/jpeg", validJpgBytes);

        // Store as private KYC document (isPublic = false)
        StoredDocumentDto stored = documentStorageService.storeFile(licenseDoc, 12L);
        assertNotNull(stored);
        assertTrue(stored.getFileUrl().startsWith("/api/documents/download/"), "Private KYC document URL must route to /download, never public");
        assertFalse(stored.getFileUrl().contains("/public/"), "Private KYC document must not expose public stream URL");
    }
}
