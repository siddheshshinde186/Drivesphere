package com.drivesphere.service;

import com.drivesphere.dto.StoredDocumentDto;
import com.drivesphere.entity.DriverVerification;
import com.drivesphere.integration.cloudinary.CloudinaryClient;
import com.drivesphere.repository.DriverVerificationRepository;
import com.drivesphere.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DocumentStorageService {

    @Value("${drivesphere.storage.local-dir:uploads/documents}")
    private String uploadDir;

    private final CloudinaryClient cloudinaryClient;
    private final DriverVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private Path rootLocation;

    private final Map<String, Long> documentOwnerUserMap = new ConcurrentHashMap<>();

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    public DocumentStorageService(CloudinaryClient cloudinaryClient, DriverVerificationRepository verificationRepository) {
        this(cloudinaryClient, verificationRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentStorageService(CloudinaryClient cloudinaryClient, DriverVerificationRepository verificationRepository, UserRepository userRepository) {
        this.cloudinaryClient = cloudinaryClient;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        try {
            Path target = Paths.get(uploadDir != null ? uploadDir : "uploads/documents");
            if (!target.isAbsolute()) {
                Path backendDir = Paths.get("backend", target.toString()).toAbsolutePath().normalize();
                if (Files.exists(backendDir) && !Files.exists(target.toAbsolutePath())) {
                    this.rootLocation = backendDir;
                } else {
                    this.rootLocation = target.toAbsolutePath().normalize();
                }
            } else {
                this.rootLocation = target.normalize();
            }
            Files.createDirectories(this.rootLocation);
            log.info("Initialized secure document storage directory at: {}", this.rootLocation);
        } catch (IOException e) {
            log.error("Could not initialize document storage location: {}", e.getMessage());
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    public StoredDocumentDto storeFile(MultipartFile file) {
        return storeFile(file, null, false);
    }

    public StoredDocumentDto storeFile(MultipartFile file, Long uploaderUserId) {
        // By default, storeFile called directly handles Driver KYC private documents
        return storeFile(file, uploaderUserId, false);
    }

    public StoredDocumentDto storePublicFile(MultipartFile file, Long uploaderUserId) {
        return storeFile(file, uploaderUserId, true);
    }

    public StoredDocumentDto storeFile(MultipartFile file, Long uploaderUserId, boolean isPublic) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty or null file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit. Actual size: " + (file.getSize() / (1024 * 1024)) + "MB");
        }

        String rawContentType = file.getContentType();
        if (rawContentType == null || !ALLOWED_CONTENT_TYPES.contains(rawContentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file type: " + rawContentType + ". Allowed formats: JPG, PNG, WEBP, PDF.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename containing path traversal characters: " + originalFilename);
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        // Validate extension matches contentType and is strictly allowed
        List<String> allowedExtensions = List.of(".jpg", ".jpeg", ".png", ".webp", ".pdf");
        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file extension: " + extension + ". Allowed: .jpg, .jpeg, .png, .webp, .pdf");
        }
        if (".pdf".equals(extension) && !"application/pdf".equalsIgnoreCase(rawContentType)) {
            throw new IllegalArgumentException("File extension does not match MIME type");
        }

        // Validate magic bytes
        try (InputStream checkStream = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = checkStream.read(header);
            if (read < 4) {
                throw new IllegalArgumentException("File content is too small or invalid.");
            }
            boolean validMagic = false;
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                validMagic = true; // JPEG
            } else if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                validMagic = true; // PNG
            } else if (header[0] == (byte) 0x25 && header[1] == (byte) 0x50 && header[2] == (byte) 0x44 && header[3] == (byte) 0x46) {
                validMagic = true; // PDF
            } else if (header[0] == (byte) 0x52 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46 && header[3] == (byte) 0x46) {
                validMagic = true; // WEBP / RIFF
            }
            if (!validMagic) {
                throw new IllegalArgumentException("File signature does not match allowed types (JPG, PNG, WEBP, PDF).");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read and validate file header: " + e.getMessage());
        }

        String storedFilename = UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + extension;

        try {
            if (this.rootLocation == null) {
                init();
            }
            Path targetLocation = this.rootLocation.resolve(storedFilename).normalize();
            if (!targetLocation.startsWith(this.rootLocation)) {
                throw new SecurityException("Cannot store file outside target directory.");
            }

            Files.createDirectories(targetLocation.getParent());

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            if (uploaderUserId != null) {
                documentOwnerUserMap.put(storedFilename, uploaderUserId);
            }

            String fileUrl = isPublic ? ("/api/documents/public/" + storedFilename) : ("/api/documents/download/" + storedFilename);
            if (isPublic && cloudinaryClient != null && cloudinaryClient.isConfigured()) {
                try {
                    String cloudUrl = cloudinaryClient.uploadBytes(file.getBytes(), originalFilename, rawContentType, false);
                    if (cloudUrl != null && !cloudUrl.isBlank()) {
                        fileUrl = cloudUrl;
                    }
                } catch (Exception ex) {
                    log.warn("Cloudinary upload failed for public asset, falling back to local URL: {}", ex.getMessage());
                }
            } else if (!isPublic && cloudinaryClient != null && cloudinaryClient.isConfigured()) {
                // For private KYC documents, upload as authenticated asset mode only, preserving access mediation through our endpoint
                try {
                    cloudinaryClient.uploadBytes(file.getBytes(), originalFilename, rawContentType, true);
                } catch (Exception ex) {
                    log.warn("Cloudinary private backup upload failed: {}", ex.getMessage());
                }
            }

            log.info("Successfully stored document: {} (size: {} bytes, url: {})", storedFilename, file.getSize(), fileUrl);

            return StoredDocumentDto.builder()
                    .filename(storedFilename)
                    .originalFilename(originalFilename)
                    .fileUrl(fileUrl)
                    .fileSize(file.getSize())
                    .contentType(rawContentType)
                    .uploadedAt(Instant.now())
                    .build();

        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to store file " + originalFilename + ": " + e.getMessage(), e);
        }
    }

    public Resource loadFileAsResource(String filename, Long currentUserId, boolean isAdmin, boolean isCustomer) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid file request.");
        }

        // If the user owns the document (e.g. uploaded customer avatar or driver document), allow viewing
        boolean isOwner = currentUserId != null && currentUserId.equals(documentOwnerUserMap.get(filename));
        if (!isOwner && currentUserId != null && userRepository != null) {
            isOwner = userRepository.findById(currentUserId)
                    .map(u -> u.getAvatarUrl() != null && u.getAvatarUrl().contains(filename))
                    .orElse(false);
        }

        // Authorization check: CUSTOMER cannot download driver documents
        if (!isOwner && isCustomer && !isAdmin) {
            throw new AccessDeniedException("Access denied: Customers are not authorized to download driver verification documents.");
        }

        // If DRIVER: check ownership
        if (!isAdmin && !isOwner) {
            List<DriverVerification> allVerifs = verificationRepository != null ? verificationRepository.findAll() : List.of();
            for (DriverVerification verif : allVerifs) {
                boolean containsFile = isFilenameInVerification(verif, filename);
                if (containsFile) {
                    if (verif.getDriver() != null && verif.getDriver().getUser() != null
                            && verif.getDriver().getUser().getId().equals(currentUserId)) {
                        isOwner = true;
                    }
                    break;
                }
            }
            if (!isOwner) {
                throw new AccessDeniedException("Access denied: You do not own this document.");
            }
        }

        return loadFileAsResource(filename);
    }

    public Resource loadPublicFileAsResource(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid file request.");
        }

        // Public stream endpoint strictly serves image assets (avatars and vehicle photos)
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png") && !lower.endsWith(".webp")) {
            log.warn("Blocked public access attempt to non-image document: {}", filename);
            throw new AccessDeniedException("Access denied: Only image assets are accessible through the public stream endpoint.");
        }

        // Strict security: Never expose private driver KYC verification documents through public stream
        if (isDriverKycDocument(filename)) {
            log.warn("Blocked public access attempt to private KYC verification document: {}", filename);
            throw new AccessDeniedException("Access denied: Private driver verification documents cannot be accessed via public stream.");
        }

        return loadFileAsResource(filename);
    }

    public boolean isDriverKycDocument(String filename) {
        if (filename == null || filename.isBlank() || verificationRepository == null) return false;
        try {
            List<DriverVerification> allVerifs = verificationRepository.findAll();
            for (DriverVerification verif : allVerifs) {
                if (isFilenameInVerification(verif, filename)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Error checking KYC document status: {}", e.getMessage());
        }
        return false;
    }

    public Resource loadFileAsResource(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid file request.");
        }

        if (this.rootLocation == null) {
            init();
        }

        try {
            Path filePath = this.rootLocation.resolve(filename).normalize();
            if (!Files.exists(filePath)) {
                Path target = Paths.get(uploadDir != null ? uploadDir : "uploads/documents");
                if (!target.isAbsolute()) {
                    Path backendPath = Paths.get("backend", target.toString()).resolve(filename).toAbsolutePath().normalize();
                    if (Files.exists(backendPath)) {
                        filePath = backendPath;
                    }
                }
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("File not found or unreadable: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("File not found: " + filename, e);
        }
    }

    private boolean isFilenameInVerification(DriverVerification v, String filename) {
        if (v == null || filename == null) return false;
        return (v.getIdentityDocUrl() != null && v.getIdentityDocUrl().endsWith(filename))
                || (v.getLicenseDocUrl() != null && v.getLicenseDocUrl().endsWith(filename))
                || (v.getPoliceVerificationDocUrl() != null && v.getPoliceVerificationDocUrl().endsWith(filename))
                || (v.getMedicalFitnessDocUrl() != null && v.getMedicalFitnessDocUrl().endsWith(filename))
                || (v.getEyeTestDocUrl() != null && v.getEyeTestDocUrl().endsWith(filename));
    }
}

