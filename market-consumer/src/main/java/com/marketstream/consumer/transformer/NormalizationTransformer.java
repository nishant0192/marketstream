package com.marketstream.consumer.transformer;

import com.marketstream.common.model.MarketEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Second stage in the ETL pipeline.
 * Normalizes market events to a canonical form before persistence:
 * - Symbol uppercased and trimmed
 * - Price truncated to 6 decimal places (HALF_UP rounding)
 * - Volume truncated to 2 decimal places
 * - Timestamp preserved as UTC (Instant is already UTC-aware)
 * - Exchange uppercased, defaults to "UNKNOWN" if null
 */
@Component
public class NormalizationTransformer {

    private static final int PRICE_SCALE = 6;
    private static final int VOLUME_SCALE = 2;

    public MarketEvent normalize(MarketEvent event) {
        return MarketEvent.builder()
                .symbol(normalizeSymbol(event.getSymbol()))
                .eventType(event.getEventType())
                .price(normalizeDecimal(event.getPrice(), PRICE_SCALE))
                .volume(event.getVolume() != null
                        ? normalizeDecimal(event.getVolume(), VOLUME_SCALE)
                        : BigDecimal.ZERO)
                .exchange(event.getExchange() != null
                        ? event.getExchange().toUpperCase().trim()
                        : "UNKNOWN")
                .timestamp(event.getTimestamp())
                .ingestionTimestamp(event.getIngestionTimestamp() != null
                        ? event.getIngestionTimestamp()
                        : Instant.now())
                .build();
    }

    private String normalizeSymbol(String symbol) {
        return symbol.toUpperCase().trim();
    }

    private BigDecimal normalizeDecimal(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
