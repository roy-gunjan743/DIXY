-- V5: Clean up all legacy unique constraints created prior to Flyway migration.
--
-- Drops any Hibernate auto-generated unique constraints like 'uklj4ca45x4skpm6ik8hxd4qjgk'
-- that might have survived previous migrations on existing PostgreSQL databases.

DO $$
DECLARE
    r RECORD;
BEGIN
    -- Drop all non-canonical constraints on threads
    FOR r IN
        SELECT c.conname
        FROM   pg_constraint c
        JOIN   pg_class      t ON t.oid = c.conrelid
        WHERE  t.relname       = 'threads'
          AND  c.contype       = 'u'
          AND  c.conname      <> 'uq_threads_thread_id_app_name_instance_id'
    LOOP
        EXECUTE 'ALTER TABLE threads DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;

    -- Drop all non-canonical unique indexes on threads
    FOR r IN
        SELECT indexname
        FROM   pg_indexes
        WHERE  tablename = 'threads'
          AND  indexname <> 'uq_threads_thread_id_app_name_instance_id'
          AND  indexdef LIKE '%UNIQUE%'
          AND  indexname NOT LIKE '%_pkey'
    LOOP
        EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(r.indexname);
    END LOOP;
END
$$;
