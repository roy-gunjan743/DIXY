package com.virtual_deadlock_detection.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "locks",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_locks_lock_id_instance_id",
        columnNames = {"lock_id", "instance_id"}
    )
)
public class LockEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Application-level lock identifier (JVM memory address of the monitor).
     * NOT globally unique — different JVM instances can produce the same value.
     * Identity must always be evaluated as (lockId + instanceId).
     */
    @Column(nullable = false)
    private String lockId;

    /**
     * Identifies the JVM/process that owns this lock record.
     * Format: "pid-<pid>" as set by EventSender.
     * Scopes lock identity so JVM-A/lock-123 != JVM-B/lock-123.
     */
    @Column(nullable = false)
    private String instanceId;

    @Column(nullable = false)
    private String lockType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_thread_id")
    private ThreadEntity ownerThread;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "lock",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<WaitEdgeEntity> waitingthread = new ArrayList<>();
}