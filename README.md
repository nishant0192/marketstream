# MarketStream

Real-time financial market data pipeline — Kafka → ETL → PostgreSQL + Redis, with a Spring Boot REST API and live monitoring dashboard.

[![CI](https://github.com/your-org/marketstream/actions/workflows/ci.yml/badge.svg)](https://github.com/your-org/marketstream/actions)

---

## Architecture

```
MarketDataGenerator (4 threads, 32 symbols)
        │
        │  Kafka  (JSON, snappy, 64KB batch)
        ▼
  market.stocks  x12 partitions
  market.forex   x6  partitions      1h retention
  market.crypto  x6  partitions
        │
        │  concurrency=8, max-poll=1000
        ▼
  ETL Consumer Pipeline
  ├─ ValidationTransformer     reject invalid / stale events
  ├─ NormalizationTransformer  uppercase, UTC, price scaling
  ├─ OHLCAggregator            1-min OHLC bars  (batch flush)
  └─ Redis write-through       price:{symbol}   30s TTL
        │
        ├──▶  PostgreSQL   market_events, ohlc_bars
        └──▶  Redis        LRU cache, 256MB max
                │
                │  cache-aside (Redis first)
                ▼
        Spring Boot REST API :8080
        Next.js Dashboard   :3000
```

---

## Quick Start

### Prerequisites
- Docker Desktop
- Git
- Node 18+ (dashboard only)

### Start

```bash
git clone https://github.com/your-org/marketstream.git
cd marketstream

# First run — build images (~3-5 min)
docker compose up --build -d

# After first build — use the start script (resets offsets, no stale events)
./scripts/start.sh
```

### Dashboard

```bash
cd dashboard
npm install
npm run dev
# Open http://localhost:3000
```

### Stop / Restart

```bash
./scripts/stop.sh
./scripts/restart.sh
./scripts/status.sh    # check what's running
```

---

## API Reference

| Endpoint | Description |
|---|---|
| `GET /api/prices/{symbol}` | Latest tick — Redis first, PostgreSQL fallback |
| `GET /api/prices/{symbol}/history?from=&to=` | 1-min OHLC bars in time range |
| `GET /api/metrics/cache` | Cache hit ratio, hits, misses |
| `GET /api/metrics/pipeline` | End-to-end latency per symbol (Producer → Redis) |
| `GET /api/health` | Liveness probe |

**Example:**
```bash
curl http://localhost:8080/api/prices/AAPL
curl http://localhost:8080/api/metrics/pipeline
```

**Pipeline metrics response:**
```json
{
  "status": "IN_SYNC",
  "avgLatencyMs": 33,
  "minLatencyMs": 20,
  "maxLatencyMs": 54,
  "cachedSymbols": 32,
  "symbols": [
    { "symbol": "AAPL", "ageMs": 41, "syncStatus": "FRESH", "lastPrice": "175.23" }
  ]
}
```

Status levels: `IN_SYNC` (<2s) · `LAGGING` (<10s) · `STALE` (>10s)

---

## Module Structure

```
marketstream/
├── market-common/          shared DTOs and entities
├── market-producer/        mock data generator → Kafka
├── market-consumer/        ETL pipeline → PostgreSQL + Redis
├── market-api/             REST API + cache-aside logic
├── dashboard/              Next.js real-time monitoring UI
├── scripts/
│   ├── start.sh            boot with auto offset reset
│   ├── stop.sh
│   ├── restart.sh
│   └── status.sh           container health + Kafka lag
├── infra/init-db.sql       PostgreSQL schema
├── docker-compose.yml
└── .github/workflows/ci.yml
```

---

## Tests

```bash
# All modules
./mvnw verify

# Single module
./mvnw test -pl market-consumer
```

| Module | Tests | Coverage |
|---|---|---|
| market-producer | 5 | Generator, counter, disable mode |
| market-consumer | 26 | Validation (11), Normalization (8), OHLC (7) |
| market-api | 14 | PriceService (7), Controller MockMvc (7) |
| **Total** | **45** | |

---

## Performance

| Metric | Target | Result |
|---|---|---|
| End-to-end latency | < 100ms | **~33ms avg** |
| Cache hit ratio | ≥ 60% | grows with traffic |
| Consumer lag | 0 | < 50 messages |
| Symbols tracked | 32 | STOCK · FOREX · CRYPTO |

Monitor live at `http://localhost:3000`.

---

## Configuration

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | Kafka broker |
| `POSTGRES_URL` | `jdbc:postgresql://postgres:5432/marketstream` | Database |
| `REDIS_HOST` / `REDIS_PORT` | `redis` / `6379` | Cache |
| `PRODUCER_THREADS` | `4` | Generator thread count |
| `SERVER_PORT` | `8080` | API port |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Messaging | Apache Kafka 7.5 |
| Cache | Redis 7.2 (Lettuce) |
| Database | PostgreSQL 16 / Hibernate 6 |
| Build | Maven (multi-module) |
| Testing | JUnit 5, Mockito, MockMvc |
| Dashboard | Next.js 16, Recharts |
| CI/CD | GitHub Actions |
| Containers | Docker Compose |
