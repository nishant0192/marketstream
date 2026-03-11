#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "Starting infrastructure..."
docker compose up -d zookeeper kafka postgres redis market-api market-producer

echo "Waiting for Kafka..."
until [ "$(docker inspect --format='{{.State.Health.Status}}' marketstream-kafka 2>/dev/null)" = "healthy" ]; do
  sleep 2
done

echo "Initializing topics..."
docker compose up kafka-init

echo "Resetting consumer offsets to latest..."
docker exec marketstream-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group market-consumer-group \
  --reset-offsets --to-latest --execute --all-topics

echo "Starting consumer..."
docker compose up -d market-consumer

echo ""
echo "MarketStream running:"
echo "  API:       http://localhost:8080"
echo "  Dashboard: http://localhost:3000"
echo ""
