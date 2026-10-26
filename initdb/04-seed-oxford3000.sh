#!/usr/bin/env bash
# Load the Oxford 3000 public topical decks on the first start of an empty database volume.
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname vocabulary_db \
  -f /docker-entrypoint-initdb.d/seed/008_seed_oxford3000.sql
