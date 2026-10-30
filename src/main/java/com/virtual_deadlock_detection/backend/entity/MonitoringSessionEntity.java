package com.virtual_deadlock_detection.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitoring_sessions")
public class MonitoringSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_pk")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_pk", nullable = false)
    private MonitoringInstanceEntity instance;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @PrePersist
    void onCreate() {
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public MonitoringInstanceEntity getInstance() { return instance; }
    public void setInstance(MonitoringInstanceEntity value) { instance = value; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime value) { endedAt = value; }
}
