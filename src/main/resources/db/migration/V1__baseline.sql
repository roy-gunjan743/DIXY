-- V1: Baseline schema -- represents the state created by ddl-auto:update
-- before Flyway was introduced.
--
-- On EXISTING databases (production / local developer DBs):
--   This migration is NEVER executed. Flyway is configured with
--   baseline-on-migrate=true / baseline-version=0, so V0 is recorded
--   as the baseline for the pre-existing schema, then V1 and V2+ run.
--
-- On FRESH databases (Testcontainers integration tests, new installs):
--   This migration runs in full and creates the initial schema.

CREATE TABLE IF NOT EXISTS threads (
    id             BIGSERIAL    PRIMARY KEY,
    thread_id      BIGINT       NOT NULL,
    app_name       VARCHAR(255),
    thread_name    VARCHAR(255) NOT NULL,
    state          VARCHAR(255) NOT NULL,
    carrier_thread VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uq_threads_thread_id_app_name UNIQUE (thread_id, app_name)
);

CREATE TABLE IF NOT EXISTS locks (
    id               BIGSERIAL    PRIMARY KEY,
    lock_id          VARCHAR(255) NOT NULL,
    lock_type        VARCHAR(255) NOT NULL,
    owner_thread_id  BIGINT       REFERENCES threads (id),
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uq_locks_lock_id UNIQUE (lock_id)
);

CREATE TABLE IF NOT EXISTS wait_edges (
    id                BIGSERIAL PRIMARY KEY,
    waiting_thread_id BIGINT    REFERENCES threads (id),
    lock_id           BIGINT    REFERENCES locks (id),
    created_at        TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS deadlock_reports (
    id                   BIGSERIAL    PRIMARY KEY,
    severity             VARCHAR(255),
    resolved             BOOLEAN,
    analysis_time_micros BIGINT,
    app_name             VARCHAR(255),
    instance_id          VARCHAR(255),
    detection_strategy   VARCHAR(255),
    detected_at          TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS deadlock_nodes (
    id          BIGSERIAL PRIMARY KEY,
    report_id   BIGINT    REFERENCES deadlock_reports (id),
    thread_id   BIGINT    REFERENCES threads (id),
    order_index INTEGER   NOT NULL
);
