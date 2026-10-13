#!/usr/bin/env bash
# =============================================================================
# PARROTO - create per-service roles + databases (X-04)
# =============================================================================
# Run by the official postgres image from /docker-entrypoint-initdb.d on the FIRST
# start of an empty data volume, before 01-init.sql (schema + seed).
# Passwords come from the container environment (docker-compose passes the same
# *_DB_PASSWORD variables that each service uses as SPRING_DATASOURCE_PASSWORD), so
# nothing secret is hard-coded in git. Values are bound with `psql -v` and quoted
# with :'var' (literal quoting done by psql -> safe for any characters).
#
# Note: changing a password in .env later does NOT update an existing volume. Either
# re-create it (`docker-compose down -v`) or run ALTER ROLE ... PASSWORD manually.
# =============================================================================
set -euo pipefail

: "${POSTGRES_USER:?POSTGRES_USER must be set by the postgres image}"

for var in AUTH_DB_PASSWORD CONTENT_DB_PASSWORD PRACTICE_DB_PASSWORD VOCABULARY_DB_PASSWORD SUBSCRIPTION_DB_PASSWORD; do
  if [ -z "${!var:-}" ]; then
    echo "00-roles.sh: environment variable $var is empty - set it in .env (see .env.example)" >&2
    exit 1
  fi
done

# psql interpolates :'var' only when reading a script (stdin / -f), not with -c.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${POSTGRES_DB:-$POSTGRES_USER}" \
  -v auth_pw="$AUTH_DB_PASSWORD" \
  -v content_pw="$CONTENT_DB_PASSWORD" \
  -v practice_pw="$PRACTICE_DB_PASSWORD" \
  -v vocabulary_pw="$VOCABULARY_DB_PASSWORD" \
  -v subscription_pw="$SUBSCRIPTION_DB_PASSWORD" <<'SQL'
CREATE ROLE auth_user WITH LOGIN PASSWORD :'auth_pw' CREATEDB;
CREATE ROLE content_user WITH LOGIN PASSWORD :'content_pw' CREATEDB;
CREATE ROLE practice_user WITH LOGIN PASSWORD :'practice_pw' CREATEDB;
CREATE ROLE vocabulary_user WITH LOGIN PASSWORD :'vocabulary_pw' CREATEDB;
CREATE ROLE subscription_user WITH LOGIN PASSWORD :'subscription_pw' CREATEDB;

CREATE DATABASE auth_db OWNER auth_user;
CREATE DATABASE content_db OWNER content_user;
CREATE DATABASE practice_db OWNER practice_user;
CREATE DATABASE vocabulary_db OWNER vocabulary_user;
CREATE DATABASE subscription_db OWNER subscription_user;
SQL

echo "00-roles.sh: created 5 service roles and databases"
