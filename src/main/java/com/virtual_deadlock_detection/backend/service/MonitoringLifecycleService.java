package com.virtual_deadlock_detection.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringLifecycleService {
    private final JdbcTemplate jdbcTemplate;

    public MonitoringLifecycleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void recordTelemetry(String instanceId, String appName) {
        String safeInstanceId = instanceId == null || instanceId.isBlank() ? "unknown" : instanceId;
        String safeAppName = appName == null || appName.isBlank() ? "unknown" : appName;

        Long instancePk = jdbcTemplate.queryForObject(
                "INSERT INTO monitoring_instances (instance_id, app_name, started_at, last_seen_at) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (instance_id) DO UPDATE SET last_seen_at = CURRENT_TIMESTAMP, " +
                "app_name = CASE WHEN EXCLUDED.app_name <> 'unknown' THEN EXCLUDED.app_name " +
                "ELSE monitoring_instances.app_name END RETURNING instance_pk",
                Long.class, safeInstanceId, safeAppName);

        jdbcTemplate.update(
                "INSERT INTO monitoring_sessions (instance_pk) " +
                "SELECT ? WHERE NOT EXISTS (SELECT 1 FROM monitoring_sessions " +
                "WHERE instance_pk = ? AND ended_at IS NULL) " +
                "ON CONFLICT (instance_pk) WHERE ended_at IS NULL DO NOTHING",
                instancePk, instancePk);
    }
}
