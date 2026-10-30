package com.virtual_deadlock_detection.backend.repository;

import com.virtual_deadlock_detection.backend.entity.LockEntity;
import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.entity.WaitEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitEdgeRepository extends JpaRepository<WaitEdgeEntity, Long> {

    List<WaitEdgeEntity> findByWaitingThread(ThreadEntity thread);

    List<WaitEdgeEntity> findByLock(LockEntity lock);

    Optional<WaitEdgeEntity> findByWaitingThreadAndLock(
            ThreadEntity thread,
            LockEntity lock
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM WaitEdgeEntity we WHERE we.waitingThread = :thread AND we.lock = :lock")
    int deleteByWaitingThreadAndLock(
            @org.springframework.data.repository.query.Param("thread") ThreadEntity thread,
            @org.springframework.data.repository.query.Param("lock") LockEntity lock
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "INSERT INTO wait_edges (waiting_thread_id, lock_id, created_at) " +
            "VALUES (:waitingThreadId, :lockId, NOW()) " +
            "ON CONFLICT (waiting_thread_id, lock_id) DO NOTHING", nativeQuery = true)
    void insertIfNotExists(
            @org.springframework.data.repository.query.Param("waitingThreadId") Long waitingThreadId,
            @org.springframework.data.repository.query.Param("lockId") Long lockId
    );

    /**
     * Loads every wait-edge together with its waiting thread, its lock, and
     * that lock's owner thread in a single query.
     *
     * GraphService.buildGraphFromDb() walks waitingThread / lock /
     * lock.ownerThread — all LAZY associations — outside of any web
     * request. CLI commands (Spring Shell) don't run inside an HTTP
     * request, so there's no Open-Session-In-View to keep the Hibernate
     * session open once findAll() returns, and touching a lazy proxy
     * afterwards throws LazyInitializationException ("no session"). The
     * JOIN FETCH here loads everything eagerly up front so the graph can be
     * built safely regardless of which caller (CLI or HTTP) invokes it.
     */
    @Query("select we from WaitEdgeEntity we " +
            "join fetch we.waitingThread " +
            "join fetch we.lock l " +
            "left join fetch l.ownerThread")
    List<WaitEdgeEntity> findAllWithThreadsAndLocks();
}