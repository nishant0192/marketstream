package com.marketstream.consumer.transformer;

import com.marketstream.common.model.EventType;
import com.marketstream.common.model.MarketEvent;
import com.marketstream.common.model.OHLCBar;
import com.marketstream.common.entity.OHLCBarEntity;
import com.marketstream.consumer.repository.OHLCBarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Third stage in the ETL pipeline.
 *
 * Accumulates ticks into 1-minute OHLC candlestick bars in memory
 * using a ConcurrentHashMap (thread-safe for concurrent consumers).
 *
 * Every 60 seconds a scheduler flushes completed windows to PostgreSQL.
 * "Completed" means the bar's period_end is in the past.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OHLCAggregator {

    private static final long WINDOW_MINUTES = 1;

    private final OHLCBarRepository ohlcBarRepository;

    /** Key: "symbol:windowStart-epoch-seconds" → accumulating bar */
    private final ConcurrentHashMap<String, OHLCBar> activeWindows = new ConcurrentHashMap<>();

    /**
     * Ingests a single tick into the appropriate 1-minute window.
     */
    public void ingest(MarketEvent event) {
        Instant windowStart = truncateToMinute(event.getTimestamp());
        String key = buildKey(event.getSymbol(), windowStart);

        activeWindows.compute(key, (k, bar) -> {
            if (bar == null) {
                // First tick in this window — set open
                return OHLCBar.builder()
                        .symbol(event.getSymbol())
                        .eventType(event.getEventType())
                        .open(event.getPrice())
                        .high(event.getPrice())
                        .low(event.getPrice())
                        .close(event.getPrice())
                        .volume(event.getVolume() != null ? event.getVolume() : BigDecimal.ZERO)
                        .periodStart(windowStart)
                        .periodEnd(windowStart.plus(WINDOW_MINUTES, ChronoUnit.MINUTES))
                        .tickCount(1)
                        .build();
            } else {
                // Update high, low, close and accumulate volume
                return OHLCBar.builder()
                        .symbol(bar.getSymbol())
                        .eventType(bar.getEventType())
                        .open(bar.getOpen())
                        .high(bar.getHigh().max(event.getPrice()))
                        .low(bar.getLow().min(event.getPrice()))
                        .close(event.getPrice())
                        .volume(bar.getVolume().add(
                                event.getVolume() != null ? event.getVolume() : BigDecimal.ZERO))
                        .periodStart(bar.getPeriodStart())
                        .periodEnd(bar.getPeriodEnd())
                        .tickCount(bar.getTickCount() + 1)
                        .build();
            }
        });
    }

    /**
     * Flushes all windows whose period_end is in the past.
     * Runs every 30 seconds to catch completed bars promptly.
     */
    @Scheduled(fixedDelay = 30_000)
    public void flushCompletedWindows() {
        Instant now = Instant.now();
        List<OHLCBar> toFlush = new ArrayList<>();

        activeWindows.entrySet().removeIf(entry -> {
            OHLCBar bar = entry.getValue();
            if (bar.getPeriodEnd().isBefore(now)) {
                toFlush.add(bar);
                return true;
            }
            return false;
        });

        if (!toFlush.isEmpty()) {
            List<OHLCBarEntity> entities = toFlush.stream()
                    .map(this::toEntity)
                    .toList();
            ohlcBarRepository.saveAll(entities);
            log.debug("Flushed {} OHLC bars to PostgreSQL", entities.size());
        }
    }

    /** Returns a snapshot of the active windows — used in tests */
    public Map<String, OHLCBar> getActiveWindows() {
        return java.util.Collections.unmodifiableMap(activeWindows);
    }

    private Instant truncateToMinute(Instant ts) {
        return ts.truncatedTo(ChronoUnit.MINUTES);
    }

    private String buildKey(String symbol, Instant windowStart) {
        return symbol + ":" + windowStart.getEpochSecond();
    }

    private OHLCBarEntity toEntity(OHLCBar bar) {
        return OHLCBarEntity.builder()
                .symbol(bar.getSymbol())
                .eventType(bar.getEventType())
                .open(bar.getOpen())
                .high(bar.getHigh())
                .low(bar.getLow())
                .close(bar.getClose())
                .volume(bar.getVolume())
                .periodStart(bar.getPeriodStart())
                .periodEnd(bar.getPeriodEnd())
                .tickCount(bar.getTickCount())
                .build();
    }
}
