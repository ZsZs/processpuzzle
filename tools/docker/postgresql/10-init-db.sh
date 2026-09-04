#!/bin/bash
# 10-init-db.sh
#
# Runs ONCE, on the first start of an empty data directory (the postgres image only executes
# /docker-entrypoint-initdb.d/ when it initialises a cluster). An existing `postgres_data` volume
# will NOT gain anything added here: reset it with
#   npm run stack-clean
#
# A SHELL script rather than the .sql file this replaced, because a .sql file in
# /docker-entrypoint-initdb.d/ cannot read an environment variable — and the application password
# was therefore hardcoded, with no way to give prod a different one. The entrypoint runs `*.sh` there
# too, in file-name order; the numeric prefix makes that ordering explicit if a second script is ever
# added.
#
# The `keycloak` database is created by the entrypoint from POSTGRES_DB; everything below is the
# ProcessPuzzle side. One database per application stack — see docs/application-stacks.md. Both
# stacks' databases are still created here even though only the testbed's application is built in
# this repository: the databases, like the realms and the bucket prefixes, are shared
# infrastructure, and the private admin backend connects to the one below — see
# docs/platform-admin-extraction.md. The identifiers are unquoted, so PostgreSQL folds them to lower
# case: `processpuzzle_testbed` and `processpuzzle_admin` ARE the databases the design document names
# in upper case. Quoting them instead would force quoting at every connection site forever.
set -e

APP_ROLE="${PROCESSPUZZLE_DB_USERNAME:-processpuzzle}"
APP_PASSWORD="${PROCESSPUZZLE_DB_PASSWORD:-processpuzzle}"

# ON_ERROR_STOP is what makes a failure here abort cluster initialisation instead of leaving a
# half-provisioned database behind that looks fine until the first application query.
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-SQL
	GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_DB} TO ${POSTGRES_USER};

	-- One role for both stacks' application databases. It is not the \`${POSTGRES_USER}\` superuser
	-- role: the backend has no business reaching Keycloak's own tables, and separating them is what
	-- makes the per-stack grants below mean anything.
	CREATE ROLE ${APP_ROLE} WITH LOGIN PASSWORD '${APP_PASSWORD}';

	CREATE DATABASE processpuzzle_testbed OWNER ${APP_ROLE};
	CREATE DATABASE processpuzzle_admin OWNER ${APP_ROLE};

	-- Owner already implies these; stated so that a later change of owner does not silently take the
	-- application's access away with it.
	GRANT ALL PRIVILEGES ON DATABASE processpuzzle_testbed TO ${APP_ROLE};
	GRANT ALL PRIVILEGES ON DATABASE processpuzzle_admin TO ${APP_ROLE};
SQL

# Hibernate creates tables in `public`, and since PostgreSQL 15 `public` is no longer writable by
# every role. Without this, ddl-auto: update fails with "permission denied for schema public" —
# a failure that looks like a connection problem and is not one. One connection per database, since
# the grants are per-database objects.
for DATABASE in processpuzzle_testbed processpuzzle_admin; do
	psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${DATABASE}" <<-SQL
		GRANT ALL ON SCHEMA public TO ${APP_ROLE};
		ALTER SCHEMA public OWNER TO ${APP_ROLE};
	SQL
done
