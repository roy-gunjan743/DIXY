-- V4: Add unique constraint on wait_edges (waiting_thread_id, lock_id)
-- Prevents duplicate wait relationships across concurrent telemetry threads and backend pods.

-- 1. Deduplicate any existing wait edges, keeping the one with the smallest id
DELETE FROM wait_edges
WHERE id NOT IN (
    SELECT MIN(id)
    FROM wait_edges
    GROUP BY waiting_thread_id, lock_id
);

-- 2. Drop constraint if it already exists (idempotency)
ALTER TABLE wait_edges
    DROP CONSTRAINT IF EXISTS uq_wait_edges_thread_lock;

-- 3. Add unique constraint on (waiting_thread_id, lock_id)
ALTER TABLE wait_edges
    ADD CONSTRAINT uq_wait_edges_thread_lock UNIQUE (waiting_thread_id, lock_id);
