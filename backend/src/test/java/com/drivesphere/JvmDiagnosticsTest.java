package com.drivesphere;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.management.*;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public class JvmDiagnosticsTest {

    @Test
    void logJvmMemoryAndClassloadingDiagnostics() {
        ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();

        System.out.println("================== JVM DIAGNOSTIC SUMMARY ==================");
        System.out.printf("Total Loaded Classes   : %d%n", classBean.getLoadedClassCount());
        System.out.printf("Total Class Load Count : %d%n", classBean.getTotalLoadedClassCount());
        System.out.printf("Unloaded Class Count   : %d%n", classBean.getUnloadedClassCount());
        System.out.println("-------------------------------------------------------------");
        System.out.printf("Heap Memory Used       : %.2f MB (Committed: %.2f MB, Max: %.2f MB)%n",
                memBean.getHeapMemoryUsage().getUsed() / (1024.0 * 1024.0),
                memBean.getHeapMemoryUsage().getCommitted() / (1024.0 * 1024.0),
                memBean.getHeapMemoryUsage().getMax() / (1024.0 * 1024.0));
        System.out.printf("Non-Heap Memory Used   : %.2f MB (Committed: %.2f MB, Max: %.2f MB)%n",
                memBean.getNonHeapMemoryUsage().getUsed() / (1024.0 * 1024.0),
                memBean.getNonHeapMemoryUsage().getCommitted() / (1024.0 * 1024.0),
                memBean.getNonHeapMemoryUsage().getMax() / (1024.0 * 1024.0));
        System.out.println("------------------- MEMORY POOLS BREAKDOWN -------------------");
        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            System.out.printf("%-30s | Used: %6.2f MB | Committed: %6.2f MB | Max: %6.2f MB%n",
                    pool.getName(),
                    usage.getUsed() / (1024.0 * 1024.0),
                    usage.getCommitted() / (1024.0 * 1024.0),
                    usage.getMax() / (1024.0 * 1024.0));
        }
        System.out.println("=============================================================");
    }
}

