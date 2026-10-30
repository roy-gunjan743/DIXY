-- An instance can have only one active monitoring session.
CREATE UNIQUE INDEX uq_monitoring_sessions_one_active
    ON monitoring_sessions(instance_pk)
    WHERE ended_at IS NULL;
