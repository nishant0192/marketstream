# MarketStream 📊

> Real-time financial market data pipeline — 50K+ events/sec through Kafka → PostgreSQL, with Redis caching and a Spring Boot REST API.

[![CI](https://github.com/your-org/marketstream/actions/workflows/ci.yml/badge.svg)](https://github.com/your-org/marketstream/actions)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        PRODUCERS                             │
│  MarketDataGenerator (4 threads, 32 symbols)                 │
│  STOCK: 17 symbols │ FOREX: 7 symbols │ CRYPTO: 8 symbols    │
└────────────────────────────┬─────────────────────────────────┘
                             │ Kafka (JSON, snappy, 64KB batch)
                  ┌──────────┴──────────┐
                  │   Kafka Topics      │
                  │  market.stocks  x12 │
                  │  market.forex   x6  │
                  │  market.crypto  x6  │
                  └──────────┬──────────┘
                             │ concurrency=4 × 3 topics = 12 threads
┌────────────────────────────▼─────────────────────────────────┐
│                     ETL PIPELINE                             │
│  1. ValidationTransformer  → reject invalid / stale events   │
│  2. NormalizationTransformer → uppercase, scale, UTC         │
│  3. OHLCAggregator         → 1-min OHLC bars (ConcurrentMap) │
│  4. PostgreSQL persist     → batch insert (size=50)          │
│  5. Redis write-through    → price:{symbol} with 30s TTL     │
└─────────────────┬──────────────────────┬─────────────────────┘
                  │                      │
          ┌───────▼───────┐    ┌─────────▼──────────┐
          │  PostgreSQL    │    │  Redis (LRU cache)  │
          │  market_events │    │  price:{symbol}     │
          │  ohlc_bars     │    │  256MB max, no disk │
          └───────┬───────┘    └─────────┬───────────┘
                  │                      │
                  └──────────┬───────────┘
                             │ cache-aside (Redis first)
                  ┌──────────▼──────────┐
                  │  Spring Boot API    │
                  │  GET /api/prices/.. │
                  │  GET /../history    │
                  │  GET /metrics/cache │
                  └─────────────────────┘
```

---

## Performance Targets

| Metric                  | Target    | How Achieved                                    |
|-------------------------|-----------|-------------------------------------------------|
| Throughput              | 50K+ /sec | 4 producer threads × 32 symbols continuous loop |
| P99 latency (end-to-end)| < 4ms     | Ingestion timestamp in message, logged at persist|
| Cache hit ratio         | ≥ 60%     | 30s Redis TTL, write-through on every consume   |
| DB write throughput     | High      | JPA batch size 50, sequence allocation 50        |

---

## Module Structure

```
marketstream/
├── market-common/        # Shared models + DTOs (MarketEvent, OHLCBar, PriceResponse)
├── market-producer/      # Mock data generator + Kafka producer
├── market-consumer/      # Kafka consumer: ETL pipeline + JPA persistence + Redis update
├── market-api/           # Spring Boot REST API with cache-aside
├── infra/
│   └── init-db.sql       # PostgreSQL schema + indexes
├── .github/workflows/
│   └── ci.yml            # GitHub Actions CI
└── docker-compose.yml    # Full stack: Zookeeper, Kafka, Postgres, Redis, all services
```

---

## Quick Start

### Prerequisites
- Docker Desktop running
- Git

### Run Everything

```bash
git clone https://github.com/your-org/marketstream.git
cd marketstream

# Start the full stack (first run ~3-5 min to build images)
docker compose up --build

# Or in detached mode
docker compose up --build -d
```

Startup order is enforced via Docker health checks:
```
Zookeeper → Kafka → kafka-init (creates topics) → Producer + Consumer + API
```

### Verify It's Working

```bash
# 1. API health check
curl http://localhost:8080/api/health

# 2. Latest price for AAPL (wait ~10s for data to flow)
curl http://localhost:8080/api/prices/AAPL | python -m json.tool

# 3. Latest price — check fromCache:true on second call
curl http://localhost:8080/api/prices/AAPL

# 4. Cache metrics (hit ratio should grow past 60% quickly)
curl http://localhost:8080/api/metrics/cache | python -m json.tool

# 5. OHLC history (after ~2 min for a completed bar)
curl "http://localhost:8080/api/prices/AAPL/history?from=2024-01-01T00:00:00Z&to=2099-01-01T00:00:00Z"
```

### Stop
```bash
docker compose down          # Keep postgres data volume
docker compose down -v       # Also delete postgres data
```

---

## API Reference

### `GET /api/prices/{symbol}`
Returns latest tick for a symbol.

**Response 200:**
```json
{
  "symbol": "AAPL",
  "eventType": "STOCK",
  "price": "175.234567",
  "volume": "4523.00",
  "exchange": "NASDAQ",
  "timestamp": "2024-01-15T10:30:00.123Z",
  "fromCache": true
}
```
**Response 404:** Symbol not yet seen.

---

### `GET /api/prices/{symbol}/history?from=&to=`
Returns 1-minute OHLC bars in a time range.

**Params:** `from`, `to` — ISO-8601 instants (e.g. `2024-01-15T10:00:00Z`)

**Response 200:** Array of OHLC bars  
**Response 204:** No bars available for range  
**Response 400:** `from` is after `to`

---

### `GET /api/metrics/cache`
Cache performance counters.

```json
{
  "cacheHits": 7523,
  "cacheMisses": 1891,
  "totalRequests": 9414,
  "hitRatio": 0.799,
  "cachedSymbols": 32
}
```

---

### `GET /api/health`
Liveness probe. Always returns `200 {"status": "UP"}`.

---

## Running Tests

```bash
# All unit tests (no external services needed)
./mvnw test -pl market-common,market-producer,market-consumer,market-api

# Single module
./mvnw test -pl market-consumer

# With verbose output
./mvnw test -pl market-api -Dsurefire.failIfNoSpecifiedTests=false
```

**Test coverage:**
| Module          | Tests | What's Covered                                      |
|-----------------|-------|-----------------------------------------------------|
| market-producer | 5     | Generator validity, counter, disable mode           |
| market-consumer | 26    | Validation (11), Normalization (8), OHLC (7)        |
| market-api      | 14    | PriceService (7), Controller MockMvc (7)            |
| **Total**       | **45**|                                                     |

---

## Performance Benchmarks

### Throughput

After `docker compose up`, run:
```bash
# Watch the consumer container logs for processed count
docker logs marketstream-consumer --follow | grep -E "Processed|Rejected"

# Check Kafka topic lag (0 lag = consumer keeping up)
docker exec marketstream-kafka \
  kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group market-consumer-group --describe
```

**Expected throughput:** 50,000–100,000 events/sec (4 threads × 32 symbols, no I/O throttling in mock generator)

### P99 Latency

The `processing_latency_ms` column in `market_events` stores ingestion-to-persist latency per message.

```sql
-- Connect to PostgreSQL
docker exec -it marketstream-postgres psql -U marketstream -d marketstream

-- P50, P95, P99 latency
SELECT
  PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY processing_latency_ms) AS p50_ms,
  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY processing_latency_ms) AS p95_ms,
  PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY processing_latency_ms) AS p99_ms
FROM market_events
WHERE processing_latency_ms IS NOT NULL;
```

**Expected result:** `p99_ms < 4`

### Cache Effectiveness

```bash
# After system warm-up (~30s):
curl http://localhost:8080/api/metrics/cache
# Expected: hitRatio > 0.60
```

**Why it works:** The consumer writes every processed tick to Redis with a 30s TTL. The API always checks Redis first. With 32 symbols being updated continuously, almost every `GET /api/prices/{symbol}` request finds a cached value.

---

## Configuration

All settings are environment-variable driven. Override in `docker-compose.yml` or pass with `-e`:

| Variable                  | Default                              | Description               |
|---------------------------|--------------------------------------|---------------------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                     | Kafka broker address      |
| `POSTGRES_URL`            | `jdbc:postgresql://localhost:5432/marketstream` | DB connection  |
| `POSTGRES_USER`           | `marketstream`                       | DB username               |
| `POSTGRES_PASSWORD`       | `marketstream`                       | DB password               |
| `REDIS_HOST`              | `localhost`                          | Redis host                |
| `REDIS_PORT`              | `6379`                               | Redis port                |
| `PRODUCER_THREADS`        | `4`                                  | Generator thread count    |
| `PRODUCER_ENABLED`        | `true`                               | Enable/disable generator  |
| `SERVER_PORT`             | `8080`                               | API server port           |

---

## Tech Stack

| Component    | Technology                                  |
|--------------|---------------------------------------------|
| Language     | Java 17                                     |
| Framework    | Spring Boot 3.2.3                           |
| Messaging    | Apache Kafka (confluentinc/cp-kafka:7.5.3)  |
| Cache        | Redis 7.2 (Lettuce client)                  |
| Database     | PostgreSQL 16                               |
| ORM          | Spring Data JPA / Hibernate 6               |
| Build        | Maven (multi-module)                        |
| Testing      | JUnit 5, Mockito, MockMvc                   |
| CI/CD        | GitHub Actions                              |
| Containers   | Docker + Docker Compose                     |
