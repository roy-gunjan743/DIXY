package com.virtual_deadlock_detection.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitoring_instances")
public class MonitoringInstanceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instance_pk")
    private Long id;

    @Column(name = "instance_id", nullable = false, unique = true)
    private String instanceId;

    @Column(name = "app_name", nullable = false)
    private String appName;

    private String hostname;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @PrePersist
    void onCreate() {
        if (startedAt == null) startedAt = OffsetDateTime.now();
        if (lastSeenAt == null) lastSeenAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String value) { instanceId = value; }
    public String getAppName() { return appName; }
    public void setAppName(String value) { appName = value; }
    public String getHostname() { return hostname; }
    public void setHostname(String value) { hostname = value; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime value) { lastSeenAt = value; }
}
