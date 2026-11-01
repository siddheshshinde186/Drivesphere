package com.drivesphere.controller;

import com.drivesphere.dto.ApiResponse;
import com.drivesphere.dto.StoredDocumentDto;
import com.drivesphere.dto.UserDto;
import com.drivesphere.service.AuthService;
import com.drivesphere.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStorageService documentStorageService;
    private final AuthService authService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<ApiResponse<StoredDocumentDto>> uploadDocument(@RequestParam("file") MultipartFile file) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized"));
        }

        StoredDocumentDto stored = documentStorageService.storeFile(file, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(stored, "Document uploaded securely"));
    }

    @GetMapping("/download/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String filename) {
        UserDto currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = currentUser.isAdmin();
        boolean isCustomer = currentUser.hasRole(com.drivesphere.entity.RoleType.ROLE_CUSTOMER) && !isAdmin;

        Resource resource = documentStorageService.loadFileAsResource(filename, currentUser.getId(), isAdmin, isCustomer);

        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(Paths.get(resource.getFile().getAbsolutePath()));
        } catch (IOException ex) {
            log.warn("Could not determine file type for: {}", filename);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * Public document streaming endpoint for avatars and public assets.
     * Operates without JWT authorization requirement so browser <img> tags render without 401 errors.
     */
    @GetMapping("/public/{filename:.+}")
    public ResponseEntity<Resource> getPublicDocument(@PathVariable String filename) {
        Resource resource = documentStorageService.loadPublicFileAsResource(filename);

        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(Paths.get(resource.getFile().getAbsolutePath()));
        } catch (IOException ex) {
            log.warn("Could not determine file type for public asset: {}", filename);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}

