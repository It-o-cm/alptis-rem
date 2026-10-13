#!/usr/bin/env bash
# Builds and launches the application in the `dev` profile for the end-to-end suite.
# Serves the statically built SPA (no Vite dev server) and exposes the dev-only
# /api/test endpoint, on H2 recreated at launch.
set -euo pipefail

PORT="${1:-8099}"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"

# Build the dev runner (bakes H2 + dev-only beans + the built UI).
mvn -q -Dquarkus.profile=dev -DskipTests -Dquarkus.quinoa.package-manager-install=false package

exec "$JAVA_HOME/bin/java" \
  -Dquarkus.profile=dev \
  -Dquarkus.http.port="$PORT" \
  -jar target/quarkus-app/quarkus-run.jar
