package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.backend.dto.TelemetryRequest;
import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.enums.ThreadState;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import com.virtual_deadlock_detection.backend.service.LockService;
import com.virtual_deadlock_detection.backend.service.TelemetryService;
import com.virtual_deadlock_detection.backend.service.ThreadService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Problem 2 -- Thread Identity
 *
 * Verifies that thread identity is scoped to (threadId + appName + instanceId).
 *
 * Without instanceId scoping, JVM-A and JVM-B of the same application (e.g.
 * appName="my-service") sharing thread ID 42 would collide into the exact same
 * database row, corrupting thread ownership, state, and wait edges across instances.
 */
@Testcontainers
@SpringBootTest
class ThreadIdentityTest {

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
    ThreadService threadService;

    @Autowired
    ThreadRepository threadRepository;

    @Autowired
    LockService lockService;

    @Autowired
    LockRepository lockRepository;

    @Autowired
    TelemetryService telemetryService;

    @BeforeEach
    void cleanDatabase() {
        lockRepository.deleteAll();
        threadRepository.deleteAll();
    }

    // -------------------------------------------------------------------
    // Test 1: same threadId & appName from DIFFERENT instances -> 2 records
    // -------------------------------------------------------------------
    @Test
    @DisplayName("JVM-A/thread-42 and JVM-B/thread-42 of same app must produce distinct records")
    void sameThreadId_differentInstances_createsDistinctRecords() {

        ThreadEntity threadFromA = new ThreadEntity();
        threadFromA.setThreadId(42L);
        threadFromA.setAppName("payment-service");
        threadFromA.setInstanceId("pid-jvm-A");
        threadFromA.setThreadName("worker-42");
        threadFromA.setState(ThreadState.RUNNABLE);

        ThreadEntity threadFromB = new ThreadEntity();
        threadFromB.setThreadId(42L);
        threadFromB.setAppName("payment-service");
        threadFromB.setInstanceId("pid-jvm-B");
        threadFromB.setThreadName("worker-42");
        threadFromB.setState(ThreadState.BLOCKED);

        ThreadEntity savedA = threadService.registerOrUpdateThread(threadFromA);
        ThreadEntity savedB = threadService.registerOrUpdateThread(threadFromB);

        assertNotEquals(savedA.getId(), savedB.getId(),
                "Same threadId from different JVM instances MUST produce distinct database rows");
        assertEquals(2, threadRepository.count(),
                "Database must contain exactly 2 thread records (one per instance)");
    }

    // -------------------------------------------------------------------
    // Test 2: same threadId from SAME instance -> idempotent update
    // -------------------------------------------------------------------
    @Test
    @DisplayName("JVM-A/thread-42 registered twice must update existing record idempotently")
    void sameThreadId_sameInstance_isIdempotent() {

        ThreadEntity first = new ThreadEntity();
        first.setThreadId(42L);
        first.setAppName("order-service");
        first.setInstanceId("pid-jvm-A");
        first.setThreadName("order-worker");
        first.setState(ThreadState.RUNNABLE);

        ThreadEntity second = new ThreadEntity();
        second.setThreadId(42L);
        second.setAppName("order-service");
        second.setInstanceId("pid-jvm-A");
        second.setThreadName("order-worker");
        second.setState(ThreadState.BLOCKED);

        ThreadEntity saved1 = threadService.registerOrUpdateThread(first);
        ThreadEntity saved2 = threadService.registerOrUpdateThread(second);

        assertEquals(saved1.getId(), saved2.getId(),
                "Same (threadId + appName + instanceId) must resolve to the same database row");
        assertEquals(1, threadRepository.count(),
                "Database must contain exactly 1 thread record for the same composite identity");
        assertEquals(ThreadState.BLOCKED, saved2.getState(),
                "State should be updated to BLOCKED");
    }

    // -------------------------------------------------------------------
    // Test 3: null instanceId receives safe 'unknown' fallback
    // -------------------------------------------------------------------
    @Test
    @DisplayName("Null instanceId receives 'unknown' fallback and is stored safely")
    void nullInstanceId_receivesUnknownFallback() {

        ThreadEntity thread = new ThreadEntity();
        thread.setThreadId(100L);
        thread.setAppName("legacy-app");
        thread.setInstanceId(null);
        thread.setThreadName("legacy-thread");
        thread.setState(ThreadState.RUNNABLE);

        ThreadEntity saved = threadService.registerOrUpdateThread(thread);

        assertEquals("unknown", saved.getInstanceId(),
                "Null instanceId must be stored as 'unknown'");
    }

    // -------------------------------------------------------------------
    // Test 4: ownerThread lookup via TelemetryService is instance-scoped
    // -------------------------------------------------------------------
    @Test
    @DisplayName("Telemetry BLOCKED event resolves ownerThread strictly within same instance")
    void ownerThreadLookup_isInstanceScoped() {

        // Setup: Thread 100 exists on both JVM-A and JVM-B
        ThreadEntity ownerA = new ThreadEntity();
        ownerA.setThreadId(100L);
        ownerA.setAppName("inventory-service");
        ownerA.setInstanceId("pid-jvm-A");
        ownerA.setThreadName("owner-on-A");
        ownerA.setState(ThreadState.RUNNABLE);
        ThreadEntity savedOwnerA = threadService.registerOrUpdateThread(ownerA);

        ThreadEntity ownerB = new ThreadEntity();
        ownerB.setThreadId(100L);
        ownerB.setAppName("inventory-service");
        ownerB.setInstanceId("pid-jvm-B");
        ownerB.setThreadName("owner-on-B");
        ownerB.setState(ThreadState.RUNNABLE);
        ThreadEntity savedOwnerB = threadService.registerOrUpdateThread(ownerB);

        // JVM-A sends a THREAD_BLOCKED event with ownerThreadId=100
        TelemetryRequest reqA = new TelemetryRequest();
        reqA.setAppName("inventory-service");
        reqA.setInstanceId("pid-jvm-A");
        reqA.setEventType("THREAD_BLOCKED");
        reqA.setThreadId(200L);
        reqA.setThreadName("waiter-on-A");
        reqA.setState(ThreadState.BLOCKED);
        reqA.setLockId("lock-inv-1");
        reqA.setLockType("MONITOR");
        reqA.setOwnerThreadId(100L);

        telemetryService.processTelemetry(reqA);

        LockEntity lock = lockRepository.findByLockIdAndInstanceId("lock-inv-1", "pid-jvm-A")
                .orElseThrow(() -> new AssertionError("Lock must be present for JVM-A"));

        assertNotNull(lock.getOwnerThread(), "Lock owner must be populated");
        assertEquals(savedOwnerA.getId(), lock.getOwnerThread().getId(),
                "Lock owner must resolve to JVM-A's thread 100, NOT JVM-B's thread 100");
    }
}