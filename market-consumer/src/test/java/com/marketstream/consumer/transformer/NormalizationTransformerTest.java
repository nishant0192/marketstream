package com.marketstream.consumer.transformer;

import com.marketstream.common.model.EventType;
import com.marketstream.common.model.MarketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NormalizationTransformer Unit Tests")
class NormalizationTransformerTest {

    private NormalizationTransformer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new NormalizationTransformer();
    }

    @Test
    @DisplayName("Symbol is uppercased and trimmed")
    void symbol_uppercasedAndTrimmed() {
        MarketEvent event = validEvent("  aapl  ");
        MarketEvent result = normalizer.normalize(event);
        assertThat(result.getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("Price is rounded to 6 decimal places (HALF_UP)")
    void price_scaledTo6Decimals() {
        MarketEvent event = validEvent("AAPL");
        event.setPrice(new BigDecimal("175.1234567")); // 7 dp → rounds to 6
        MarketEvent result = normalizer.normalize(event);
        assertThat(result.getPrice().scale()).isEqualTo(6);
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("175.123457"));
    }

    @Test
    @DisplayName("Volume is rounded to 2 decimal places")
    void volume_scaledTo2Decimals() {
        MarketEvent event = validEvent("AAPL");
        event.setVolume(new BigDecimal("1234.5678"));
        MarketEvent result = normalizer.normalize(event);
        assertThat(result.getVolume().scale()).isEqualTo(2);
        assertThat(result.getVolume()).isEqualByComparingTo(new BigDecimal("1234.57"));
    }

    @Test
    @DisplayName("Null volume becomes ZERO")
    void nullVolume_becomesZero() {
        MarketEvent event = validEvent("AAPL");
        event.setVolume(null);
        MarketEvent result = normalizer.normalize(event);
        assertThat(result.getVolume()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Exchange is uppercased and trimmed")
    void exchange_uppercased() {
        MarketEvent event = validEvent("AAPL");
        event.setExchange("  nasdaq  ");
        MarketEvent result = normalizer.normalize(event);
        assertThat(result.getExchange()).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Null exchange becomes UNKNOWN")
    void nullExchange_becomesUnknown() {
        MarketEvent event = validEvent("AAPL");
        event.setExchange(null);
        MarketEvent result = normalizer.normalize(event);
        assertThat(result.getExchange()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Timestamp is preserved unchanged")
    void timestamp_preserved() {
        Instant ts = Instant.parse("2024-01-15T10:30:00Z");
        MarketEvent event = validEvent("AAPL");
        event.setTimestamp(ts);
        MarketEvent result = normalizer.normalize(event);
        assertThat(result.getTimestamp()).isEqualTo(ts);
    }

    @Test
    @DisplayName("Null ingestion timestamp gets set to now")
    void nullIngestionTimestamp_setsToNow() {
        MarketEvent event = validEvent("AAPL");
        event.setIngestionTimestamp(null);
        Instant before = Instant.now();
        MarketEvent result = normalizer.normalize(event);
        Instant after = Instant.now();
        assertThat(result.getIngestionTimestamp()).isBetween(before, after);
    }

    // Helper
    private MarketEvent validEvent(String symbol) {
        return MarketEvent.builder()
                .symbol(symbol)
                .eventType(EventType.STOCK)
                .price(new BigDecimal("175.500000"))
                .volume(new BigDecimal("1000.00"))
                .exchange("NASDAQ")
                .timestamp(Instant.now())
                .ingestionTimestamp(Instant.now())
                .build();
    }
}
