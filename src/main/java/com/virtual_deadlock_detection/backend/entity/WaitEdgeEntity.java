package com.virtual_deadlock_detection.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "wait_edges")
public class WaitEdgeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiting_thread_id")
    private ThreadEntity waitingThread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lock_id")
    private LockEntity lock;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist(){
        this.createdAt=LocalDateTime.now();
    }

}