#!/bin/bash
# 10-init-db.sh
#
# Runs automatically on the first start of an empty data directory, and is also invoked by the
# `postgres-init` Compose one-shot after PostgreSQL becomes healthy. It is deliberately idempotent:
# a new application database can therefore be added to an existing infrastructure volume without
# resetting Keycloak or application data.
#
# A SHELL script rather than the .sql file this replaced, because a .sql file in
# /docker-entrypoint-initdb.d/ cannot read an environment variable — and the application password
# was therefore hardcoded, with no way to give prod a different one. The entrypoint runs `*.sh` there
# too, in file-name order; the numeric prefix makes that ordering explicit if a second script is ever
# added.
#
# The `keycloak` database is created by the entrypoint from POSTGRES_DB; everything below is the
# ProcessPuzzle side. One database per application type — see docs/application-stacks.md. The Custom
# database is pooled across customer backends; each deployment is isolated by its authenticated
# organization key. These databases, like realms and bucket prefixes, are shared infrastructure even
# when their application images are built in the private repository. The identifiers are unquoted, so
# PostgreSQL folds them to lower case: `processpuzzle_testbed`, `processpuzzle_admin`, and
# `processpuzzle_custom` ARE the databases the design document names in upper case. Quoting them
# instead would force quoting at every connection site forever.
set -e

# NO fallback on the password, and the `:?` is load-bearing. Two reasons this one is different
# from the role name beside it:
#
#   1. CREATE ROLE below is guarded by a pg_roles existence check and there is no ALTER ROLE
#      anywhere in this file. The password is therefore written ONCE, on the first start against an
#      empty data directory, and is immutable afterwards. A fallback here does not produce a
#      recoverable mistake; it bakes `processpuzzle` into the volume permanently, and every later
#      correction of the environment variable is silently ignored.
#   2. The compose files no longer default it either, so this was the last layer that could turn a
#      forgotten deployment secret into a plausible wrong value. Removing only the compose default
#      would have left the role created as `processpuzzle` with nothing to show for it.
#
# Unlike a compose file, this is a real shell, so `:?` behaves as written: the message goes to
# stderr and the script exits non-zero, which fails container init loudly instead of half-way.
APP_ROLE="${PROCESSPUZZLE_DB_USERNAME:-processpuzzle}"
APP_PASSWORD="${PROCESSPUZZLE_DB_PASSWORD:?PROCESSPUZZLE_DB_PASSWORD must be set; the application role password is written once, on first start against an empty volume, and cannot be changed afterwards}"

# Keep the Keycloak database owner explicit. The command must be separate from CREATE DATABASE,
# which PostgreSQL forbids inside a transaction block.
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
	-c "GRANT ALL PRIVILEGES ON DATABASE ${POSTGRES_DB} TO ${POSTGRES_USER};"

# One role for all application databases. It is not the \`${POSTGRES_USER}\` superuser role: the
# backends have no business reaching Keycloak's own tables, and separating them makes the per-stack
# grants meaningful.
if ! psql --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" --tuples-only --no-align \
	-c "SELECT 1 FROM pg_roles WHERE rolname = '${APP_ROLE}'" | grep -qx "1"; then
	psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
		-c "CREATE ROLE ${APP_ROLE} WITH LOGIN PASSWORD '${APP_PASSWORD}';"
fi

# Hibernate creates tables in `public`, and since PostgreSQL 15 `public` is no longer writable by
# every role. Without this, ddl-auto: update fails with "permission denied for schema public" —
# a failure that looks like a connection problem and is not one. One connection per database, since
# the grants are per-database objects.
for DATABASE in processpuzzle_testbed processpuzzle_admin processpuzzle_custom; do
	if ! psql --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" --tuples-only --no-align \
		-c "SELECT 1 FROM pg_database WHERE datname = '${DATABASE}'" | grep -qx "1"; then
		psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
			-c "CREATE DATABASE ${DATABASE} OWNER ${APP_ROLE};"
	fi

	psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${DATABASE}" <<-SQL
		GRANT ALL PRIVILEGES ON DATABASE ${DATABASE} TO ${APP_ROLE};
		GRANT ALL ON SCHEMA public TO ${APP_ROLE};
		ALTER SCHEMA public OWNER TO ${APP_ROLE};
	SQL
done
