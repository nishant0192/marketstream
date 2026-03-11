package com.marketstream.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Candlestick / OHLC (Open-High-Low-Close) bar for a given symbol and time
 * window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OHLCBar {

    private String symbol;
    private EventType eventType;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;

    /** Start of the aggregation window (inclusive) */
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant periodStart;

    /** End of the aggregation window (exclusive) */
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant periodEnd;

    /** Number of ticks that contributed to this bar */
    private long tickCount;
}
