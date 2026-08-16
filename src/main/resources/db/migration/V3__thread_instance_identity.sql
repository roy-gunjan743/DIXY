-- V3: Problem 2 -- Thread identity must be scoped to the JVM/process instance.
--
-- ROOT CAUSE:
--   The old unique constraint on (thread_id, app_name) assumes that app_name
--   is sufficient scope. But two JVM instances of the same application
--   (e.g. appName='my-app', instanceId='pid-1001' and instanceId='pid-1002')
--   share the same thread ID space (thread 42 on JVM-A and thread 42 on JVM-B).
--   Without instance_id scoping, thread records collide, corrupting ownership,
--   state, and wait edges across instances.
--
-- FIX:
--   1. Add instance_id column to threads table.
--   2. Backfill existing rows with 'unknown' so NOT NULL constraint is safe.
--   3. Enforce instance_id NOT NULL.
--   4. Drop old unique constraint on (thread_id, app_name).
--   5. Add composite UNIQUE constraint (thread_id, app_name, instance_id).

-- Step 1: Add instance_id column (nullable initially)
ALTER TABLE threads ADD COLUMN IF NOT EXISTS instance_id VARCHAR(255);

-- Step 2: Backfill existing rows
UPDATE threads SET instance_id = 'unknown' WHERE instance_id IS NULL;

-- Step 3: Enforce NOT NULL
ALTER TABLE threads ALTER COLUMN instance_id SET NOT NULL;

-- Step 4: Drop old unique constraints on threads
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname
        FROM   pg_constraint c
        JOIN   pg_class      t ON t.oid = c.conrelid
        WHERE  t.relname       = 'threads'
          AND  c.contype       = 'u'
    LOOP
        EXECUTE 'ALTER TABLE threads DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END
$$;

-- Step 5: Add composite unique constraint
ALTER TABLE threads
    ADD CONSTRAINT uq_threads_thread_id_app_name_instance_id UNIQUE (thread_id, app_name, instance_id);