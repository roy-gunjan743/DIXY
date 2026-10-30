package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.backend.dto.TelemetryRequest;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import com.virtual_deadlock_detection.backend.enums.ThreadState;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import com.virtual_deadlock_detection.backend.repository.WaitEdgeRepository;
import com.virtual_deadlock_detection.backend.service.LockService;
import com.virtual_deadlock_detection.backend.service.TelemetryService;
import com.virtual_deadlock_detection.backend.service.ThreadService;
import com.virtual_deadlock_detection.backend.service.WaitEdgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Problem 3 -- Multi-Instance Database Concurrency & Consistency
 *
 * Verifies that:
 * 1. Concurrent telemetry ingestion across multiple threads does NOT exhaust
 *    the Hikari connection pool or deadlock on in-memory Java monitors.
 * 2. Concurrent wait-edge creations for the same (thread, lock) are atomic and idempotent.
 * 3. Concurrent thread/lock upserts across multiple simulated backend nodes succeed cleanly.
 */
@Testcontainers
@SpringBootTest
class MultiInstanceConsistencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("virtual_deadlock_detection");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    TelemetryService telemetryService;

    @Autowired
    ThreadService threadService;

    @Autowired
    LockService lockService;

    @Autowired
    WaitEdgeService waitEdgeService;

    @Autowired
    ThreadRepository threadRepository;

    @Autowired
    LockRepository lockRepository;

    @Autowired
    WaitEdgeRepository waitEdgeRepository;

    @BeforeEach
    void setUp() {
        waitEdgeRepository.deleteAll();
        lockRepository.deleteAll();
        threadRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent telemetry ingestion across 20 threads executes cleanly without connection exhaustion")
    void concurrentTelemetryIngestionDoesNotExhaustConnectionPool() throws Exception {
        int threadCount = 20;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int r = 0; r < requestsPerThread; r++) {
                        TelemetryRequest req = new TelemetryRequest();
                        req.setThreadId((long) (1000 + threadIndex));
                        req.setThreadName("Worker-" + threadIndex);
                        req.setAppName("multi-instance-app");
                        req.setInstanceId("jvm-" + (threadIndex % 3)); // 3 JVM instances
                        req.setState(ThreadState.BLOCKED);
                        req.setEventType("THREAD_BLOCKED");
                        req.setLockId("Lock-" + (r % 5));
                        req.setLockType("java.util.concurrent.locks.ReentrantLock");
                        req.setOwnerThreadId(9999L);
                        req.setOwnerThreadName("Master-Thread");

                        telemetryService.processTelemetry(req);
                        successCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent requests should complete within 30 seconds without pool starvation");
        assertTrue(errors.isEmpty(), "No concurrent requests should fail with database or connection errors: " + errors);
        assertEquals(threadCount * requestsPerThread, successCount.get());
    }

    @Test
    @DisplayName("Concurrent wait edge creation for the same thread and lock is idempotent")
    void concurrentWaitEdgeCreationIsIdempotent() throws Exception {
        ThreadEntity thread = new ThreadEntity();
        thread.setThreadId(501L);
        thread.setAppName("app-1");
        thread.setInstanceId("inst-1");
        thread.setThreadName("ContendingThread");
        thread.setState(ThreadState.BLOCKED);
        thread = threadService.registerOrUpdateThread(thread);

        LockEntity lock = new LockEntity();
        lock.setLockId("ResourceLock-1");
        lock.setInstanceId("inst-1");
        lock.setLockType("synchronized");
        lock = lockService.registerOrUpdateLock(lock);

        int concurrency = 16;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        List<WaitEdgeEntity> createdEdges = new CopyOnWriteArrayList<>();

        final ThreadEntity fThread = thread;
        final LockEntity fLock = lock;

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    WaitEdgeEntity edge = waitEdgeService.createWaitEdge(fThread, fLock);
                    createdEdges.add(edge);
                } catch (Exception e) {
                    fail("Concurrent wait edge creation threw exception: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(concurrency, createdEdges.size());
        // In DB there must be exactly 1 unique wait edge
        List<WaitEdgeEntity> allEdges = waitEdgeRepository.findAll();
        assertEquals(1, allEdges.size(), "Wait edges must be deduplicated by unique constraint");
    }
    @Test
    @DisplayName("Lock acquisition removes only the acquiring thread's wait edge")
    void lockAcquisitionRemovesOnlyAcquiringThreadsWaitEdge() {
        TelemetryRequest threadBWait = new TelemetryRequest();
        threadBWait.setThreadId(502L);
        threadBWait.setThreadName("Thread-B");
        threadBWait.setAppName("wait-edge-lifecycle-app");
        threadBWait.setInstanceId("wait-edge-test-instance");
        threadBWait.setState(ThreadState.BLOCKED);
        threadBWait.setEventType("THREAD_BLOCKED");
        threadBWait.setLockId("shared-lock");
        threadBWait.setLockType("synchronized");
        threadBWait.setOwnerThreadId(501L);
        threadBWait.setOwnerThreadName("Owner-Thread");
        telemetryService.processTelemetry(threadBWait);

        TelemetryRequest threadCWait = new TelemetryRequest();
        threadCWait.setThreadId(503L);
        threadCWait.setThreadName("Thread-C");
        threadCWait.setAppName("wait-edge-lifecycle-app");
        threadCWait.setInstanceId("wait-edge-test-instance");
        threadCWait.setState(ThreadState.BLOCKED);
        threadCWait.setEventType("THREAD_BLOCKED");
        threadCWait.setLockId("shared-lock");
        threadCWait.setLockType("synchronized");
        threadCWait.setOwnerThreadId(501L);
        threadCWait.setOwnerThreadName("Owner-Thread");
        telemetryService.processTelemetry(threadCWait);

        assertEquals(2, waitEdgeRepository.findAll().size());

        TelemetryRequest threadBAcquire = new TelemetryRequest();
        threadBAcquire.setThreadId(502L);
        threadBAcquire.setThreadName("Thread-B");
        threadBAcquire.setAppName("wait-edge-lifecycle-app");
        threadBAcquire.setInstanceId("wait-edge-test-instance");
        threadBAcquire.setState(ThreadState.RUNNABLE);
        threadBAcquire.setEventType("LOCK_ACQUIRE");
        threadBAcquire.setLockId("shared-lock");
        threadBAcquire.setLockType("synchronized");
        telemetryService.processTelemetry(threadBAcquire);

        List<WaitEdgeEntity> remainingEdges =
                waitEdgeRepository.findAllWithThreadsAndLocks();

        assertEquals(1, remainingEdges.size());
        assertEquals(503L,
                remainingEdges.get(0).getWaitingThread().getThreadId());
    }
    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Lock release clears ownership but preserves other waiters")
    void lockReleasePreservesOtherWaiters() {
        sendLockEvent(601L, "Owner-A", "release-instance", "release-lock", "LOCK_ACQUIRE", null);
        sendLockEvent(602L, "Waiter-B", "release-instance", "release-lock", "THREAD_BLOCKED", 601L);
        assertEquals(1, waitEdgeRepository.findAll().size());
        sendLockEvent(601L, "Owner-A", "release-instance", "release-lock", "LOCK_RELEASE", null);
        LockEntity saved = lockRepository.findByLockIdAndInstanceId("release-lock", "release-instance").orElseThrow();
        assertNull(saved.getOwnerThread(), "Released lock should have no owner");
        assertEquals(1, waitEdgeRepository.findAll().size(), "Other waiters must remain recorded");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Stale release cannot clear a newer lock owner")
    void staleReleaseCannotClearNewOwner() {
        sendLockEvent(611L, "Owner-A", "stale-instance", "stale-lock", "LOCK_ACQUIRE", null);
        sendLockEvent(612L, "Owner-B", "stale-instance", "stale-lock", "THREAD_BLOCKED", 611L);
        sendLockEvent(612L, "Owner-B", "stale-instance", "stale-lock", "LOCK_ACQUIRE", null);
        sendLockEvent(611L, "Owner-A", "stale-instance", "stale-lock", "LOCK_RELEASE", null);
        LockEntity saved = lockRepository.findByLockIdAndInstanceId("stale-lock", "stale-instance").orElseThrow();
        assertNotNull(saved.getOwnerThread());
        assertEquals(612L, saved.getOwnerThread().getThreadId(), "New owner must not be cleared by stale release");
    }

    private void sendLockEvent(Long threadId, String threadName, String instanceId, String lockId, String eventType, Long ownerThreadId) {
        TelemetryRequest request = new TelemetryRequest();
        request.setThreadId(threadId);
        request.setThreadName(threadName);
        request.setAppName("lock-lifecycle-test");
        request.setInstanceId(instanceId);
        request.setState(eventType.equals("THREAD_BLOCKED") ? ThreadState.BLOCKED : ThreadState.RUNNABLE);
        request.setEventType(eventType);
        request.setLockId(lockId);
        request.setLockType("synchronized");
        if (ownerThreadId != null) {
            request.setOwnerThreadId(ownerThreadId);
            request.setOwnerThreadName("Owner-Thread");
        }
        telemetryService.processTelemetry(request);
    }

}
