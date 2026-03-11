#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

check() {
  local name=$1
  local container=$2
  local status
  status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null)
  local health
  health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null)

  if [ -z "$status" ]; then
    printf "  %-28s \033[90mNOT FOUND\033[0m\n" "$name"
  elif [ "$status" = "running" ]; then
    if [ -n "$health" ] && [ "$health" != "<no value>" ]; then
      if [ "$health" = "healthy" ]; then
        printf "  %-28s \033[32mRUNNING\033[0m  (healthy)\n" "$name"
      else
        printf "  %-28s \033[33mRUNNING\033[0m  ($health)\n" "$name"
      fi
    else
      printf "  %-28s \033[32mRUNNING\033[0m\n" "$name"
    fi
  elif [ "$status" = "exited" ]; then
    local code
    code=$(docker inspect --format='{{.State.ExitCode}}' "$container" 2>/dev/null)
    if [ "$code" = "0" ]; then
      printf "  %-28s \033[90mEXITED\033[0m   (code 0 — completed OK)\n" "$name"
    else
      printf "  %-28s \033[31mEXITED\033[0m   (code $code)\n" "$name"
    fi
  else
    printf "  %-28s \033[33m%s\033[0m\n" "$name" "$status"
  fi
}

echo ""
echo "  MarketStream — Service Status"
echo "  ─────────────────────────────────────────"
check "Zookeeper"       marketstream-zookeeper
check "Kafka"           marketstream-kafka
check "Kafka Init"      marketstream-kafka-init
check "PostgreSQL"      marketstream-postgres
check "Redis"           marketstream-redis
check "Producer"        marketstream-producer
check "Consumer"        marketstream-consumer
check "API"             marketstream-api
echo "  ─────────────────────────────────────────"

echo ""
echo "  Kafka Consumer Lag"
echo "  ─────────────────────────────────────────"
docker exec marketstream-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group market-consumer-group 2>/dev/null \
  | awk 'NR==1 || /market\./ {printf "  %s\n", $0}' \
  || echo "  Kafka not reachable"

echo ""
echo "  Endpoints"
echo "  ─────────────────────────────────────────"
printf "  %-28s %s\n" "API Health"      "http://localhost:8080/api/health"
printf "  %-28s %s\n" "Pipeline Metrics" "http://localhost:8080/api/metrics/pipeline"
printf "  %-28s %s\n" "Cache Metrics"   "http://localhost:8080/api/metrics/cache"
printf "  %-28s %s\n" "Dashboard"       "http://localhost:3000"
echo ""
