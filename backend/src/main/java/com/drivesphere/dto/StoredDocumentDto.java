package com.drivesphere.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredDocumentDto {
    private String filename;
    private String originalFilename;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private Instant uploadedAt;
}
