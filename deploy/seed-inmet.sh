#!/bin/sh
# Loads every INMET year (2000..current) into a fresh instance, straight from portal.inmet.gov.br.
# Run next to docker-compose.prod.yml after `docker compose up -d`:  sh seed-inmet.sh [first-year] [last-year]
# Each year is downloaded and queued inside the backend (no proxy timeouts); imports run one at a time.
# The R map cache refreshes itself after each imported year. Set COMPOSE="podman compose" when using Podman.
set -u

first=${1:-2000}
last=${2:-$(date -u +%Y)}
compose="${COMPOSE:-docker compose} -f docker-compose.prod.yml"
failed=""

year=$first
while [ "$year" -le "$last" ]; do
  printf 'INMET %s: ' "$year"
  # The token stays inside the container ($ADMIN_TOKEN is expanded there, not on this host).
  if ! $compose exec -T backend sh -c \
    "wget -qO- -T 1800 --post-data='' --header=\"X-Admin-Token: \$ADMIN_TOKEN\" 'http://localhost:8080/api/admin/inmet-sync?year=$year'"; then
    failed="$failed $year"
  fi
  echo
  year=$((year + 1))
done

echo "Follow the imports with: $compose logs -f backend"
if [ -n "$failed" ]; then
  echo "Failed years:$failed (retry one with: sh seed-inmet.sh YEAR YEAR)"
  exit 1
fi
