#!/bin/sh
# Container entrypoint.
#
# Render's Postgres only exposes a single DATABASE_URL (postgresql://user:pass@host:port/db),
# while application.yml reads DB_HOST / DB_PORT separately. If DB_HOST isn't
# already set (docker-compose and k8s set it), derive host and port from
# DATABASE_URL. DB_NAME / DB_USERNAME / DB_PASSWORD are passed in separately.
set -e

if [ -z "$DB_HOST" ] && [ -n "$DATABASE_URL" ]; then
  hostport="${DATABASE_URL##*@}"      # host:port/db?params
  hostport="${hostport%%/*}"          # host:port
  export DB_HOST="${hostport%%:*}"
  case "$hostport" in
    *:*) export DB_PORT="${hostport##*:}" ;;
    *)   export DB_PORT="5432" ;;
  esac
  echo "[entrypoint] Using database ${DB_HOST}:${DB_PORT}"
fi

exec java -jar /app/app.jar "$@"
