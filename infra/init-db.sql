-- MarketStream Database Schema
-- Runs once at PostgreSQL container startup via docker-entrypoint-initdb.d

-- Sequences (JPA @SequenceGenerator references these)
CREATE SEQUENCE IF NOT EXISTS market_event_seq
    START WITH 1
    INCREMENT BY 50
    CACHE 50;

CREATE SEQUENCE IF NOT EXISTS ohlc_seq
    START WITH 1
    INCREMENT BY 20
    CACHE 20;

-- Raw market event ticks
CREATE TABLE IF NOT EXISTS market_events (
    id                    BIGINT         DEFAULT nextval('market_event_seq') PRIMARY KEY,
    symbol                VARCHAR(32)    NOT NULL,
    event_type            VARCHAR(10)    NOT NULL,
    price                 NUMERIC(20,6)  NOT NULL,
    volume                NUMERIC(20,2),
    exchange              VARCHAR(32),
    timestamp             TIMESTAMPTZ    NOT NULL,
    ingestion_timestamp   TIMESTAMPTZ,
    processing_latency_ms BIGINT,
    created_at            TIMESTAMPTZ    DEFAULT NOW() NOT NULL
);

-- Composite index: symbol + timestamp DESC for latest-price queries
CREATE INDEX IF NOT EXISTS idx_market_events_symbol_ts
    ON market_events (symbol, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_market_events_event_type
    ON market_events (event_type);

-- 1-minute OHLC candlestick bars
CREATE TABLE IF NOT EXISTS ohlc_bars (
    id           BIGINT         DEFAULT nextval('ohlc_seq') PRIMARY KEY,
    symbol       VARCHAR(32)    NOT NULL,
    event_type   VARCHAR(10)    NOT NULL,
    open         NUMERIC(20,6)  NOT NULL,
    high         NUMERIC(20,6)  NOT NULL,
    low          NUMERIC(20,6)  NOT NULL,
    close        NUMERIC(20,6)  NOT NULL,
    volume       NUMERIC(20,2)  NOT NULL,
    period_start TIMESTAMPTZ    NOT NULL,
    period_end   TIMESTAMPTZ    NOT NULL,
    tick_count   BIGINT         NOT NULL DEFAULT 0
);

-- Unique bar per symbol per minute
CREATE UNIQUE INDEX IF NOT EXISTS uq_ohlc_symbol_period
    ON ohlc_bars (symbol, period_start);

CREATE INDEX IF NOT EXISTS idx_ohlc_symbol_start
    ON ohlc_bars (symbol, period_start DESC);

CREATE INDEX IF NOT EXISTS idx_ohlc_event_type
    ON ohlc_bars (event_type);
