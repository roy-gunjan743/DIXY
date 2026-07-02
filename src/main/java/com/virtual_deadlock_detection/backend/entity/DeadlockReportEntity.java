package com.virtual_deadlock_detection.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deadlock_reports")
public class DeadlockReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String severity;

    private boolean resolved;

    private String analysisTimeMicros;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    @OneToMany(mappedBy = "report",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<DeadlockReportEntity>nodes=new ArrayList<>();

    @PrePersist
    public void prePersist(){
        detectedAt = LocalDateTime.now();
    }
}