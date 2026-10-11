package com.ravi.jfrmcp;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for creating representative, synthetic JFR recordings programmatically for automated testing and CI.
 */
public class TestRecordingFactory {

    private static final Logger log = LoggerFactory.getLogger(TestRecordingFactory.class);

    // Dummy lock object representing an inventory service monitor
    public static class InventoryService {
        private final Object lock = new Object();
        private long counter = 0;

        public void processOrder() {
            synchronized (lock) {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                counter++;
            }
        }

        public long getCounter() {
            synchronized (lock) {
                return counter;
            }
        }
    }

    public static class OrderService {
        public static double calculatePrice(int iterations) {
            double sum = 0.0;
            for (int i = 0; i < iterations; i++) {
                sum += Math.sin(i) * Math.cos(i);
            }
            return sum;
        }
    }

    public static class DummyOrderPayload {
        private final byte[] data;

        public DummyOrderPayload(int size) {
            this.data = new byte[size];
        }

        public int getSize() {
            return data.length;
        }
    }

    /**
     * Generates a realistic performance incident recording containing:
     * - CPU hotspots in calculatePrice
     * - Lock contention on InventoryService
     * - Garbage collections via System.gc()
     * - Object allocations
     * - Thrown exceptions
     */
    public static Path createRealisticRecording(Path targetFile) throws Exception {
        Path dest = targetFile != null ? targetFile : Files.createTempFile("realistic-incident-", ".jfr");
        if (dest.getParent() != null && !Files.exists(dest.getParent())) {
            Files.createDirectories(dest.getParent());
        }

        try (Recording recording = new Recording()) {
            recording.enable("jdk.ExecutionSample").with("period", "1 ms");
            recording.enable("jdk.CPULoad").with("period", "50 ms");
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.GarbageCollection");
            recording.enable("jdk.OldGarbageCollection");
            recording.enable("jdk.GCHeapSummary");
            recording.enable("jdk.ObjectAllocationInNewTLAB");
            recording.enable("jdk.ObjectAllocationOutsideTLAB");
            recording.enable("jdk.JavaExceptionThrow");
            recording.enable("jdk.JavaErrorThrow");
            recording.enable("jdk.JavaThreadStatistics");
            recording.enable("jdk.JVMInformation");
            recording.enable("jdk.OSInformation");

            recording.start();

            // 1. Generate CPU hotspot
            for (int i = 0; i < 50; i++) {
                OrderService.calculatePrice(200_000);
            }

            // 2. Generate allocations
            List<DummyOrderPayload> payloads = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                payloads.add(new DummyOrderPayload(8192));
            }

            // 3. Generate GC events
            System.gc();

            // 4. Generate Exception events
            for (int i = 0; i < 15; i++) {
                try {
                    throw new IllegalArgumentException("Invalid order payload sequence " + i);
                } catch (IllegalArgumentException ignored) {}
            }

            // 5. Generate Lock Contention across concurrent threads
            InventoryService inventoryService = new InventoryService();
            int threadCount = 6;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicBoolean running = new AtomicBoolean(true);

            List<Thread> threads = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                Thread worker = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int k = 0; k < 10; k++) {
                            inventoryService.processOrder();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }, "InventoryWorker-" + t);
                threads.add(worker);
                worker.start();
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);

            // Wait a moment for events to flush to buffer
            Thread.sleep(100);

            recording.stop();
            recording.dump(dest);
        }

        return dest;
    }

    /**
     * Generates a basic empty recording with minimal events.
     */
    public static Path createEmptyRecording() throws Exception {
        Path dest = Files.createTempFile("empty-test-", ".jfr");
        try (Recording recording = new Recording()) {
            recording.start();
            recording.stop();
            recording.dump(dest);
        }
        return dest;
    }

    /**
     * Generates a demo production recording in recordings/demo-production.jfr if requested.
     */
    public static void main(String[] args) {
        try {
            Path target = Paths.get("recordings/demo-production.jfr");
            System.out.println("Generating demo fixture at " + target.toAbsolutePath() + "...");
            createRealisticRecording(target);
            System.out.println("Generated demo recording successfully: " + Files.size(target) + " bytes");
        } catch (Exception e) {
            System.err.println("Failed to generate demo recording: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
