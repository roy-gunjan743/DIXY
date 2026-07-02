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
@Table(name = "locks")
public class LockEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true)
    private String lockId;

    @Column(nullable = false)
    private String lockType;

    @ManyToOne(fetch =FetchType.LAZY)
    @JoinColumn(name ="owner_thread_id")
    private ThreadEntity ownerThread;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();}

    @OneToMany(mappedBy = "lock",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<WaitEdgeEntity>waitingthread=new ArrayList<>();


}