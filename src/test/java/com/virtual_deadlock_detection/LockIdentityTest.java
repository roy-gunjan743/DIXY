package com.virtual_deadlock_detection;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.repository.LockRepository;
import com.virtual_deadlock_detection.backend.service.LockService;
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
 * Problem 1 -- Lock Identity
 *
 * Verifies that lock identity is scoped to (lockId + instanceId), not lockId alone.
 *
 * Without the fix, JVM-A and JVM-B sending the same lock_id (e.g. a JVM memory
 * address that happens to be the same in both processes) would produce a single
 * lock record, silently merging ownership and wait-edge state across instances.
 */
@Testcontainers
@SpringBootTest
class LockIdentityTest {

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
    LockService lockService;

    @Autowired
    LockRepository lockRepository;

    @BeforeEach
    void cleanLocks() {
        lockRepository.deleteAll();
    }

    // -------------------------------------------------------------------
    // Test 1: same lockId from DIFFERENT instances -> two distinct rows
    // -------------------------------------------------------------------
    @Test
    @DisplayName("JVM-A/lock-123 and JVM-B/lock-123 must produce distinct records")
    void sameLockId_differentInstances_createsDistinctRecords() {

        LockEntity lockFromA = new LockEntity();
        lockFromA.setLockId("123");
        lockFromA.setLockType("MONITOR");
        lockFromA.setInstanceId("pid-jvm-A");

        LockEntity lockFromB = new LockEntity();
        lockFromB.setLockId("123");
        lockFromB.setLockType("MONITOR");
        lockFromB.setInstanceId("pid-jvm-B");

        LockEntity savedA = lockService.registerOrUpdateLock(lockFromA);
        LockEntity savedB = lockService.registerOrUpdateLock(lockFromB);

        assertNotEquals(savedA.getId(), savedB.getId(),
                "Same lockId from different JVM instances MUST produce different database rows");
        assertEquals(2, lockRepository.count(),
                "Database must contain exactly 2 lock records (one per instance)");
    }

    // -------------------------------------------------------------------
    // Test 2: same lockId from the SAME instance -> one row (idempotent)
    // -------------------------------------------------------------------
    @Test
    @DisplayName("JVM-A/lock-456 registered twice must remain a single record")
    void sameLockId_sameInstance_isIdempotent() {

        LockEntity first = new LockEntity();
        first.setLockId("456");
        first.setLockType("MONITOR");
        first.setInstanceId("pid-jvm-A");

        LockEntity second = new LockEntity();
        second.setLockId("456");
        second.setLockType("MONITOR");
        second.setInstanceId("pid-jvm-A");

        LockEntity saved1 = lockService.registerOrUpdateLock(first);
        LockEntity saved2 = lockService.registerOrUpdateLock(second);

        assertEquals(saved1.getId(), saved2.getId(),
                "Same (lockId + instanceId) must resolve to the same database row");
        assertEquals(1, lockRepository.count(),
                "Database must contain exactly 1 lock record for the same composite identity");
    }

    // -------------------------------------------------------------------
    // Test 3: null instanceId receives the safe "unknown" fallback
    // -------------------------------------------------------------------
    @Test
    @DisplayName("Null instanceId receives 'unknown' fallback and is stored correctly")
    void nullInstanceId_receivesUnknownFallback() {

        LockEntity lock = new LockEntity();
        lock.setLockId("789");
        lock.setLockType("MONITOR");
        lock.setInstanceId(null); // simulates old agent that does not send instanceId

        LockEntity saved = lockService.registerOrUpdateLock(lock);

        assertEquals("unknown", saved.getInstanceId(),
                "Null instanceId must be stored as 'unknown', not throw NullPointerException");
    }

    // -------------------------------------------------------------------
    // Test 4: three instances, same lockId -> three distinct rows
    // -------------------------------------------------------------------
    @Test
    @DisplayName("Three JVMs with the same lockId produce three distinct records")
    void sameLockId_threeInstances_createsThreeRecords() {

        for (String instance : new String[]{"pid-A", "pid-B", "pid-C"}) {
            LockEntity lock = new LockEntity();
            lock.setLockId("999");
            lock.setLockType("MONITOR");
            lock.setInstanceId(instance);
            lockService.registerOrUpdateLock(lock);
        }

        assertEquals(3, lockRepository.count(),
                "Three distinct instances must produce three distinct lock records");
    }
}