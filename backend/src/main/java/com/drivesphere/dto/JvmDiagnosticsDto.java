package com.drivesphere.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JvmDiagnosticsDto {

    private long loadedClassCount;
    private long totalLoadedClassCount;
    private long unloadedClassCount;

    private double heapUsedMb;
    private double heapCommittedMb;
    private double heapMaxMb;

    private double nonHeapUsedMb;
    private double nonHeapCommittedMb;

    private double metaspaceUsedMb;
    private double metaspaceCommittedMb;
    private double metaspaceMaxMb;

    private double compressedClassSpaceUsedMb;
    private double compressedClassSpaceCommittedMb;
    private double compressedClassSpaceMaxMb;

    private double codeCacheUsedMb;
    private double codeCacheCommittedMb;
    private double codeCacheMaxMb;

    private double totalEstimatedJvmMb;
    private double containerRamLimitMb;
    private String metaspaceStatus;

    private Map<String, String> memoryPools;
}

