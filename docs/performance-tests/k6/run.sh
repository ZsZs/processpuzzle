#!/usr/bin/env bash
# Runs a k6 phase with the client metrics pushed into the SUT's Prometheus (same time axis as the
# server dashboards). Needs tunnel/published port 9099 to the SUT's Prometheus.
#   PROM=http://<sut>:9099 BASE_URL=http://<sut>:9090 KEYCLOAK_URL=http://<sut>:7070 ./run.sh users
set -euo pipefail
cd "$(dirname "$0")"
phase="${1:?usage: run.sh <baseline|users|stress|spike|soak|uploads|seed>}"
script=scenarios/main.js; [[ "$phase" == seed ]] && script=seed/seed.js
export K6_PROMETHEUS_RW_SERVER_URL="${PROM:-http://127.0.0.1:9099}/api/v1/write"
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"
exec k6 run -o experimental-prometheus-rw --tag testid="${phase}-$(date +%H%M)" \
  -e PHASE="$phase" -e BASE_URL="${BASE_URL:?set BASE_URL}" -e KEYCLOAK_URL="${KEYCLOAK_URL:?set KEYCLOAK_URL}" \
  ${EXTRA_K6_ARGS:-} "$script"
