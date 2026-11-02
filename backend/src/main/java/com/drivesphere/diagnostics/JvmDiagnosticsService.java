package com.drivesphere.diagnostics;

import com.drivesphere.dto.JvmDiagnosticsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-safe JVM Memory and Metaspace Diagnostics Service.
 * Logs memory distribution upon startup and provides live metrics via API.
 */
@Service
@Slf4j
public class JvmDiagnosticsService {

    private static final double MB = 1024.0 * 1024.0;

    @EventListener(ApplicationReadyEvent.class)
    public void logDiagnosticsOnStartup() {
        JvmDiagnosticsDto diag = captureDiagnostics();
        log.info(String.format(
                "%n================== DRIVESPHERE JVM MEMORY DIAGNOSTICS ==================%n" +
                "Loaded Classes         : %d%n" +
                "Heap Memory (MB)       : Used %.2f | Committed %.2f | Max %.2f%n" +
                "Metaspace (MB)         : Used %.2f | Committed %.2f | Max %.2f%n" +
                "Compressed Class Space : Used %.2f | Committed %.2f | Max %.2f%n" +
                "Code Cache (MB)        : Used %.2f | Committed %.2f | Max %.2f%n" +
                "Total Non-Heap (MB)    : Used %.2f | Committed %.2f%n" +
                "Estimated JVM RSS (MB) : %.2f / %.2f Container Budget%n" +
                "Metaspace Health       : %s%n" +
                "========================================================================",
                diag.getLoadedClassCount(),
                diag.getHeapUsedMb(), diag.getHeapCommittedMb(), diag.getHeapMaxMb(),
                diag.getMetaspaceUsedMb(), diag.getMetaspaceCommittedMb(), diag.getMetaspaceMaxMb(),
                diag.getCompressedClassSpaceUsedMb(), diag.getCompressedClassSpaceCommittedMb(), diag.getCompressedClassSpaceMaxMb(),
                diag.getCodeCacheUsedMb(), diag.getCodeCacheCommittedMb(), diag.getCodeCacheMaxMb(),
                diag.getNonHeapUsedMb(), diag.getNonHeapCommittedMb(),
                diag.getTotalEstimatedJvmMb(), diag.getContainerRamLimitMb(),
                diag.getMetaspaceStatus()));
    }

    public JvmDiagnosticsDto captureDiagnostics() {
        ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();

        double heapUsed = memBean.getHeapMemoryUsage().getUsed() / MB;
        double heapCommitted = memBean.getHeapMemoryUsage().getCommitted() / MB;
        double heapMax = memBean.getHeapMemoryUsage().getMax() / MB;

        double nonHeapUsed = memBean.getNonHeapMemoryUsage().getUsed() / MB;
        double nonHeapCommitted = memBean.getNonHeapMemoryUsage().getCommitted() / MB;

        double metaspaceUsed = 0.0;
        double metaspaceCommitted = 0.0;
        double metaspaceMax = 0.0;

        double compClassUsed = 0.0;
        double compClassCommitted = 0.0;
        double compClassMax = 0.0;

        double codeCacheUsed = 0.0;
        double codeCacheCommitted = 0.0;
        double codeCacheMax = 0.0;

        Map<String, String> poolMap = new LinkedHashMap<>();

        for (MemoryPoolMXBean pool : pools) {
            String name = pool.getName();
            MemoryUsage u = pool.getUsage();
            double used = u.getUsed() / MB;
            double committed = u.getCommitted() / MB;
            double max = u.getMax() > 0 ? u.getMax() / MB : -1.0;

            poolMap.put(name, String.format("Used: %.2f MB, Committed: %.2f MB, Max: %.2f MB", used, committed, max));

            if ("Metaspace".equalsIgnoreCase(name)) {
                metaspaceUsed = used;
                metaspaceCommitted = committed;
                metaspaceMax = max;
            } else if ("Compressed Class Space".equalsIgnoreCase(name)) {
                compClassUsed = used;
                compClassCommitted = committed;
                compClassMax = max;
            } else if (name != null && name.toLowerCase().contains("code")) {
                codeCacheUsed += used;
                codeCacheCommitted += committed;
                if (max > 0) codeCacheMax += max;
            }
        }

        double totalEstimatedRss = heapCommitted + nonHeapCommitted + 15.0; // +15MB thread stacks & native runtime
        String status = metaspaceMax > 0 && metaspaceUsed > (metaspaceMax * 0.90)
                ? "WARNING: Metaspace usage above 90% threshold"
                : "HEALTHY: Metaspace usage within safe bounds";

        return JvmDiagnosticsDto.builder()
                .loadedClassCount(classBean.getLoadedClassCount())
                .totalLoadedClassCount(classBean.getTotalLoadedClassCount())
                .unloadedClassCount(classBean.getUnloadedClassCount())
                .heapUsedMb(round(heapUsed))
                .heapCommittedMb(round(heapCommitted))
                .heapMaxMb(round(heapMax))
                .nonHeapUsedMb(round(nonHeapUsed))
                .nonHeapCommittedMb(round(nonHeapCommitted))
                .metaspaceUsedMb(round(metaspaceUsed))
                .metaspaceCommittedMb(round(metaspaceCommitted))
                .metaspaceMaxMb(round(metaspaceMax))
                .compressedClassSpaceUsedMb(round(compClassUsed))
                .compressedClassSpaceCommittedMb(round(compClassCommitted))
                .compressedClassSpaceMaxMb(round(compClassMax))
                .codeCacheUsedMb(round(codeCacheUsed))
                .codeCacheCommittedMb(round(codeCacheCommitted))
                .codeCacheMaxMb(round(codeCacheMax))
                .totalEstimatedJvmMb(round(totalEstimatedRss))
                .containerRamLimitMb(512.0)
                .metaspaceStatus(status)
                .memoryPools(poolMap)
                .build();
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}

