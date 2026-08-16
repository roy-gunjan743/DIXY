-- V2: Problem 1 -- Lock identity must be scoped to the JVM/process instance.
--
-- ROOT CAUSE:
--   The old unique constraint on lock_id alone treats lock_id as globally
--   unique. But lock_id values are JVM object-identity memory addresses
--   (e.g. "140234567891234"). Two different JVM instances can have monitors
--   at the same address. Without instance scoping, JVM-A lock 0x1A2B3C and
--   JVM-B lock 0x1A2B3C are stored as the same lock record, corrupting
--   ownership and wait-edge state for both instances.
--
-- FIX:
--   1. Add instance_id column to locks.
--   2. Backfill existing rows with 'unknown' so NOT NULL constraint is safe.
--   3. Make instance_id NOT NULL.
--   4. Drop the old single-column unique constraint (any name Hibernate gave it).
--   5. Replace with composite UNIQUE (lock_id, instance_id).

-- Step 1: Add instance_id column (nullable initially to handle existing rows)
ALTER TABLE locks ADD COLUMN IF NOT EXISTS instance_id VARCHAR(255);

-- Step 2: Backfill any existing rows
UPDATE locks SET instance_id = 'unknown' WHERE instance_id IS NULL;

-- Step 3: Enforce NOT NULL
ALTER TABLE locks ALTER COLUMN instance_id SET NOT NULL;

-- Step 4: Drop every single-column unique constraint on locks.lock_id
--   Hibernate can generate different names depending on the naming strategy
--   version (e.g. 'locks_lock_id_key', 'uk_...', etc.).
--   The DO block finds and drops all of them safely.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname
        FROM   pg_constraint c
        JOIN   pg_class      t ON t.oid = c.conrelid
        JOIN   pg_attribute  a ON a.attrelid = t.oid
                               AND a.attnum = c.conkey[1]
        WHERE  t.relname       = 'locks'
          AND  c.contype       = 'u'           -- unique constraint
          AND  array_length(c.conkey, 1) = 1   -- single-column only
          AND  a.attname       = 'lock_id'
    LOOP
        EXECUTE 'ALTER TABLE locks DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END
$$;

-- Step 5: Add the composite unique constraint
ALTER TABLE locks
    ADD CONSTRAINT uq_locks_lock_id_instance_id UNIQUE (lock_id, instance_id);
