package com.marketstream.consumer.transformer;

import com.marketstream.common.model.EventType;
import com.marketstream.common.model.MarketEvent;
import com.marketstream.common.model.OHLCBar;
import com.marketstream.consumer.repository.OHLCBarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OHLCAggregator Unit Tests")
class OHLCAggregatorTest {

    @Mock
    private OHLCBarRepository ohlcBarRepository;

    private OHLCAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new OHLCAggregator(ohlcBarRepository);
    }

    @Test
    @DisplayName("First tick creates a new OHLC bar with open=high=low=close=price")
    void firstTick_createsNewBar() {
        MarketEvent event = tickAt("AAPL", "150.00", Instant.now());
        aggregator.ingest(event);

        Map<String, OHLCBar> windows = aggregator.getActiveWindows();
        assertThat(windows).hasSize(1);
        OHLCBar bar = windows.values().iterator().next();

        assertThat(bar.getSymbol()).isEqualTo("AAPL");
        assertThat(bar.getOpen()).isEqualByComparingTo("150.00");
        assertThat(bar.getHigh()).isEqualByComparingTo("150.00");
        assertThat(bar.getLow()).isEqualByComparingTo("150.00");
        assertThat(bar.getClose()).isEqualByComparingTo("150.00");
        assertThat(bar.getTickCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Second tick updates high, low, close but NOT open")
    void secondTick_updatesHighLowClose() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        aggregator.ingest(tickAt("AAPL", "150.00", now));
        aggregator.ingest(tickAt("AAPL", "155.00", now.plusSeconds(10)));

        OHLCBar bar = aggregator.getActiveWindows().values().iterator().next();
        assertThat(bar.getOpen()).isEqualByComparingTo("150.00");
        assertThat(bar.getHigh()).isEqualByComparingTo("155.00");
        assertThat(bar.getLow()).isEqualByComparingTo("150.00");
        assertThat(bar.getClose()).isEqualByComparingTo("155.00");
    }

    @Test
    @DisplayName("Lower price tick updates low correctly")
    void lowerPriceTick_updatesLow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        aggregator.ingest(tickAt("AAPL", "150.00", now));
        aggregator.ingest(tickAt("AAPL", "140.00", now.plusSeconds(5)));

        OHLCBar bar = aggregator.getActiveWindows().values().iterator().next();
        assertThat(bar.getLow()).isEqualByComparingTo("140.00");
        assertThat(bar.getClose()).isEqualByComparingTo("140.00");
        assertThat(bar.getOpen()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Volume accumulates across ticks within window")
    void volume_accumulates() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        aggregator.ingest(tickWithVolume("AAPL", "150.00", "100.00", now));
        aggregator.ingest(tickWithVolume("AAPL", "151.00", "200.00", now.plusSeconds(5)));
        aggregator.ingest(tickWithVolume("AAPL", "152.00", "300.00", now.plusSeconds(10)));

        OHLCBar bar = aggregator.getActiveWindows().values().iterator().next();
        assertThat(bar.getVolume()).isEqualByComparingTo("600.00");
        assertThat(bar.getTickCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Ticks in different minutes create separate windows")
    void differentMinutes_createSeparateWindows() {
        Instant minute1 = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant minute2 = minute1.plus(1, ChronoUnit.MINUTES);

        aggregator.ingest(tickAt("AAPL", "150.00", minute1));
        aggregator.ingest(tickAt("AAPL", "155.00", minute2.plusSeconds(5)));

        assertThat(aggregator.getActiveWindows()).hasSize(2);
    }

    @Test
    @DisplayName("Different symbols have independent windows")
    void differentSymbols_independentWindows() {
        Instant now = Instant.now();
        aggregator.ingest(tickAt("AAPL", "150.00", now));
        aggregator.ingest(tickAt("MSFT", "380.00", now));

        assertThat(aggregator.getActiveWindows()).hasSize(2);
    }

    @Test
    @DisplayName("flushCompletedWindows persists bars whose period_end is past")
    void flushCompletedWindows_persistsCompletedBars() {
        // Use a timestamp from 2 minutes ago — this bar is complete
        Instant pastMinute = Instant.now().minus(2, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        aggregator.ingest(tickAt("AAPL", "150.00", pastMinute));

        aggregator.flushCompletedWindows();

        verify(ohlcBarRepository).saveAll(anyList());
        assertThat(aggregator.getActiveWindows()).isEmpty();
    }

    // Helpers
    private MarketEvent tickAt(String symbol, String price, Instant ts) {
        return MarketEvent.builder()
                .symbol(symbol).eventType(EventType.STOCK)
                .price(new BigDecimal(price)).volume(new BigDecimal("100.00"))
                .exchange("NASDAQ").timestamp(ts).ingestionTimestamp(ts).build();
    }

    private MarketEvent tickWithVolume(String symbol, String price, String volume, Instant ts) {
        return MarketEvent.builder()
                .symbol(symbol).eventType(EventType.STOCK)
                .price(new BigDecimal(price)).volume(new BigDecimal(volume))
                .exchange("NASDAQ").timestamp(ts).ingestionTimestamp(ts).build();
    }
}
