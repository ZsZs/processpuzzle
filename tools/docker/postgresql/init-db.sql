-- init-db.sql
--
-- Runs ONCE, on the first start of an empty data directory (the postgres image only executes
-- /docker-entrypoint-initdb.d/ when it initialises a cluster). An existing `postgres_data` volume
-- will NOT gain anything added here: reset it with
--   docker compose -f tools/docker/docker-compose-ci.yaml down -v
--
-- The `keycloak` database is created by the entrypoint from POSTGRES_DB; everything below is the
-- ProcessPuzzle side. One database per application stack — see docs/application-stacks.md. Both
-- stacks' databases are still created here even though only the testbed's application is built in
-- this repository: the databases, like the realms and the bucket prefixes, are shared
-- infrastructure, and the private admin backend connects to the one below — see
-- docs/platform-admin-extraction.md. The
-- identifiers are unquoted, so PostgreSQL folds them to lower case: `processpuzzle_testbed` and
-- `processpuzzle_admin` ARE the databases the design document names in upper case. Quoting them
-- instead would force quoting at every connection site forever.

GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;

-- One role for both stacks' application databases. It is not the `keycloak` superuser role: the
-- backend has no business reaching Keycloak's own tables, and separating them is what makes the
-- per-stack grants below mean anything.
CREATE ROLE processpuzzle WITH LOGIN PASSWORD 'processpuzzle';

CREATE DATABASE processpuzzle_testbed OWNER processpuzzle;
CREATE DATABASE processpuzzle_admin OWNER processpuzzle;

-- Owner already implies these; stated so that a later change of owner does not silently take the
-- application's access away with it.
GRANT ALL PRIVILEGES ON DATABASE processpuzzle_testbed TO processpuzzle;
GRANT ALL PRIVILEGES ON DATABASE processpuzzle_admin TO processpuzzle;

-- Hibernate creates tables in `public`, and since PostgreSQL 15 `public` is no longer writable by
-- every role. Without this, ddl-auto: update fails with "permission denied for schema public" —
-- a failure that looks like a connection problem and is not one.
\connect processpuzzle_testbed
GRANT ALL ON SCHEMA public TO processpuzzle;
ALTER SCHEMA public OWNER TO processpuzzle;

\connect processpuzzle_admin
GRANT ALL ON SCHEMA public TO processpuzzle;
ALTER SCHEMA public OWNER TO processpuzzle;
