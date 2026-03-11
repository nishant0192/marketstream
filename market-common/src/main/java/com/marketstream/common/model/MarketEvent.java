package com.marketstream.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Core event model representing a single market data tick.
 * Used as the Kafka message payload across all topics.
 *
 * Instant fields are serialized as epoch-millis numbers (JavaTimeModule handles
 * this).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketEvent {

    /** Ticker symbol e.g. AAPL, EUR/USD, BTC-USDT */
    private String symbol;

    /** Category of the market instrument */
    private EventType eventType;

    /** Current price of the instrument */
    private BigDecimal price;

    /** Trade volume for this tick */
    private BigDecimal volume;

    /** Exchange reporting the price */
    private String exchange;

    /** Epoch milliseconds when this tick was generated at source */
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, timezone = "UTC")
    private Instant timestamp;

    /** Ingestion timestamp injected at producer for end-to-end latency tracking */
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, timezone = "UTC")
    private Instant ingestionTimestamp;
}
