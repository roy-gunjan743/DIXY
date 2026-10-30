-- Central identity for each monitored JVM instance.
CREATE TABLE monitoring_instances (
    instance_pk BIGSERIAL PRIMARY KEY,
    instance_id VARCHAR(255) NOT NULL UNIQUE,
    app_name VARCHAR(255) NOT NULL,
    hostname VARCHAR(255),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Each monitoring run belongs to one JVM instance.
CREATE TABLE monitoring_sessions (
    session_pk BIGSERIAL PRIMARY KEY,
    instance_pk BIGINT NOT NULL REFERENCES monitoring_instances(instance_pk) ON DELETE RESTRICT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_monitoring_session_dates
        CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_monitoring_sessions_instance
    ON monitoring_sessions(instance_pk);

CREATE INDEX idx_monitoring_sessions_active
    ON monitoring_sessions(instance_pk, started_at)
    WHERE ended_at IS NULL;
