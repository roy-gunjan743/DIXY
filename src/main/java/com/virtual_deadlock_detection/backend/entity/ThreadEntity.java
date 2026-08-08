package com.virtual_deadlock_detection.backend.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;


import com.virtual_deadlock_detection.backend.enums.ThreadState;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "threads",
        uniqueConstraints = @UniqueConstraint(columnNames = {"threadId", "appName"}))
public class ThreadEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long threadId;

    private String appName;

    @Column(nullable = false)
    private String threadName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThreadState state;

    private String carrierThread;
    @OneToMany(mappedBy = "ownerThread",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<LockEntity> ownedLocks = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist(){
        this.createdAt=LocalDateTime.now();
    }

    @OneToMany(mappedBy = "waitingThread",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<WaitEdgeEntity>waitingEdges=new ArrayList<>();

    @OneToMany(mappedBy = "thread",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<DeadlockNodeEntity> deadlockNodes = new ArrayList<>();


}