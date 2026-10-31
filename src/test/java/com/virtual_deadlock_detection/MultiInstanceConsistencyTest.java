
package com.virtual_deadlock_detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

@Testcontainers
@SpringBootTest
class MultiInstanceConsistencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("virtual_deadlock_detection");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port",
                () -> redis.getMappedPort(6379));
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

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        waitEdgeRepository.deleteAll();
        lockRepository.deleteAll();
        threadRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent telemetry ingestion across 20 threads executes cleanly")
    void concurrentTelemetryIngestionDoesNotExhaustConnectionPool()
            throws Exception {

        int threadCount = 20;
        int requestsPerThread = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

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
                        req.setInstanceId("jvm-" + (threadIndex % 3));
                        req.setState(ThreadState.BLOCKED);
                        req.setEventType("THREAD_BLOCKED");
                        req.setLockId("Lock-" + (r % 5));
                        req.setLockType(
                                "java.util.concurrent.locks.ReentrantLock");
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

        assertTrue(completed,
                "All concurrent requests should complete within 30 seconds");

        assertTrue(errors.isEmpty(),
                "Concurrent requests should not fail: " + errors);

        assertEquals(threadCount * requestsPerThread,
                successCount.get());
    }

    @Test
    @DisplayName("Concurrent wait edge creation is idempotent")
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

        ExecutorService executor =
                Executors.newFixedThreadPool(concurrency);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);

        List<WaitEdgeEntity> createdEdges =
                new CopyOnWriteArrayList<>();

        final ThreadEntity fThread = thread;
        final LockEntity fLock = lock;

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    WaitEdgeEntity edge =
                            waitEdgeService.createWaitEdge(fThread, fLock);

                    createdEdges.add(edge);

                } catch (Exception e) {
                    fail("Wait edge creation failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(concurrency, createdEdges.size());

        List<WaitEdgeEntity> allEdges =
                waitEdgeRepository.findAll();

        assertEquals(1, allEdges.size(),
                "Wait edges must be deduplicated");
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

        sendLockEvent(601L, "Owner-A", "release-instance",
                "release-lock", "LOCK_ACQUIRE", null);

        sendLockEvent(602L, "Waiter-B", "release-instance",
                "release-lock", "THREAD_BLOCKED", 601L);

        assertEquals(1, waitEdgeRepository.findAll().size());

        sendLockEvent(601L, "Owner-A", "release-instance",
                "release-lock", "LOCK_RELEASE", null);

        LockEntity saved = lockRepository
                .findByLockIdAndInstanceId(
                        "release-lock", "release-instance")
                .orElseThrow();

        assertNull(saved.getOwnerThread(),
                "Released lock should have no owner");

        assertEquals(1, waitEdgeRepository.findAll().size(),
                "Other waiters must remain recorded");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Stale release cannot clear a newer lock owner")
    void staleReleaseCannotClearNewOwner() {

        sendLockEvent(611L, "Owner-A", "stale-instance",
                "stale-lock", "LOCK_ACQUIRE", null);

        sendLockEvent(612L, "Owner-B", "stale-instance",
                "stale-lock", "THREAD_BLOCKED", 611L);

        sendLockEvent(612L, "Owner-B", "stale-instance",
                "stale-lock", "LOCK_ACQUIRE", null);

        sendLockEvent(611L, "Owner-A", "stale-instance",
                "stale-lock", "LOCK_RELEASE", null);

        LockEntity saved = lockRepository
                .findByLockIdAndInstanceId(
                        "stale-lock", "stale-instance")
                .orElseThrow();

        assertNotNull(saved.getOwnerThread());

        assertEquals(612L,
                saved.getOwnerThread().getThreadId(),
                "New owner must not be cleared by stale release");
    }

    // ---------------------------------------------------------
    // MONITORING SESSION LIFECYCLE TESTS
    // ---------------------------------------------------------

    @Test
    @DisplayName("MONITOR_STOP closes active session without requiring threadId")
    void monitorStopClosesActiveSessionWithoutThreadId() {

        String instanceId = "monitor-stop-test-instance";

        TelemetryRequest startRequest = new TelemetryRequest();
        startRequest.setInstanceId(instanceId);
        startRequest.setAppName("monitor-stop-test-app");
        startRequest.setThreadId(701L);
        startRequest.setThreadName("Test-Thread");
        startRequest.setState(ThreadState.RUNNABLE);
        startRequest.setEventType("THREAD_START");

        telemetryService.processTelemetry(startRequest);

        Long instancePk = jdbcTemplate.queryForObject(
                "SELECT instance_pk FROM monitoring_instances WHERE instance_id = ?",
                Long.class,
                instanceId
        );

        assertNotNull(instancePk);

        Integer activeBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitoring_sessions " +
                "WHERE instance_pk = ? AND ended_at IS NULL",
                Integer.class,
                instancePk
        );

        assertEquals(1, activeBefore);

        TelemetryRequest stopRequest = new TelemetryRequest();
        stopRequest.setInstanceId(instanceId);
        stopRequest.setEventType("MONITOR_STOP");

        var response = telemetryService.processTelemetry(stopRequest);

        assertTrue(response.isSuccess());

        Integer activeAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitoring_sessions " +
                "WHERE instance_pk = ? AND ended_at IS NULL",
                Integer.class,
                instancePk
        );

        Integer closedSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitoring_sessions " +
                "WHERE instance_pk = ? AND ended_at IS NOT NULL",
                Integer.class,
                instancePk
        );

        assertEquals(0, activeAfter,
                "There should be no active session after MONITOR_STOP");

        assertEquals(1, closedSessions,
                "The original session should be closed");
    }

    @Test
    @DisplayName("MONITOR_STOP does not create a replacement session")
    void monitorStopDoesNotCreateNewSession() {

        String instanceId = "monitor-stop-no-recreate";

        TelemetryRequest startRequest = new TelemetryRequest();
        startRequest.setInstanceId(instanceId);
        startRequest.setAppName("monitor-stop-test-app");
        startRequest.setThreadId(702L);
        startRequest.setThreadName("Test-Thread");
        startRequest.setState(ThreadState.RUNNABLE);
        startRequest.setEventType("THREAD_START");

        telemetryService.processTelemetry(startRequest);

        Long instancePk = jdbcTemplate.queryForObject(
                "SELECT instance_pk FROM monitoring_instances WHERE instance_id = ?",
                Long.class,
                instanceId
        );

        assertNotNull(instancePk);

        telemetryService.processTelemetry(createMonitorStopRequest(instanceId));

        Integer totalSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitoring_sessions WHERE instance_pk = ?",
                Integer.class,
                instancePk
        );

        Integer activeSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitoring_sessions " +
                "WHERE instance_pk = ? AND ended_at IS NULL",
                Integer.class,
                instancePk
        );

        assertEquals(1, totalSessions,
                "MONITOR_STOP must not create another session");

        assertEquals(0, activeSessions,
                "The original session must remain closed");
    }

    private TelemetryRequest createMonitorStopRequest(String instanceId) {
        TelemetryRequest request = new TelemetryRequest();
        request.setInstanceId(instanceId);
        request.setEventType("MONITOR_STOP");
        return request;
    }

    private void sendLockEvent(
            Long threadId,
            String threadName,
            String instanceId,
            String lockId,
            String eventType,
            Long ownerThreadId) {

        TelemetryRequest request = new TelemetryRequest();

        request.setThreadId(threadId);
        request.setThreadName(threadName);
        request.setAppName("lock-lifecycle-test");
        request.setInstanceId(instanceId);

        request.setState(
                eventType.equals("THREAD_BLOCKED")
                        ? ThreadState.BLOCKED
                        : ThreadState.RUNNABLE
        );

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
