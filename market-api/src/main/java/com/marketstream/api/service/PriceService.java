package com.marketstream.api.service;

import com.marketstream.api.repository.ApiMarketEventRepository;
import com.marketstream.api.repository.ApiOHLCBarRepository;
import com.marketstream.common.dto.CacheMetricsResponse;
import com.marketstream.common.dto.PipelineMetricsResponse;
import com.marketstream.common.dto.PriceResponse;
import com.marketstream.common.model.EventType;
import com.marketstream.common.entity.MarketEventEntity;
import com.marketstream.common.entity.OHLCBarEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business logic for the market-api module.
 *
 * Implements the cache-aside (lazy-loading) pattern:
 * 1. Check Redis for cached latest price
 * 2. On hit → return from cache (increment hit counter)
 * 3. On miss → query PostgreSQL, populate cache, return (increment miss
 * counter)
 *
 * The hit/miss counters enable measurement of the ≥60% cache effectiveness
 * target.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final RedisService redisService;
    private final ApiMarketEventRepository eventRepository;
    private final ApiOHLCBarRepository ohlcRepository;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    // ---- Latest Price ----

    public Optional<PriceResponse> getLatestPrice(String symbol) {
        String upperSymbol = symbol.toUpperCase().trim();

        // Cache-aside: try Redis first
        Optional<String> cached = redisService.get(upperSymbol);
        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            log.debug("Cache HIT for symbol={}", upperSymbol);
            return Optional.of(parseRedisValue(upperSymbol, cached.get(), true));
        }

        // Cache miss — query PostgreSQL
        cacheMisses.incrementAndGet();
        log.debug("Cache MISS for symbol={}, querying PostgreSQL", upperSymbol);

        Optional<MarketEventEntity> entity = eventRepository.findLatestBySymbol(upperSymbol);
        if (entity.isEmpty()) {
            return Optional.empty();
        }

        MarketEventEntity e = entity.get();

        // Populate cache for next request
        String cacheValue = buildCacheValue(e.getPrice(), e.getVolume(), e.getExchange(),
                e.getEventType(), e.getTimestamp());
        redisService.set(upperSymbol, cacheValue);

        return Optional.of(PriceResponse.builder()
                .symbol(e.getSymbol())
                .eventType(e.getEventType())
                .price(e.getPrice())
                .volume(e.getVolume())
                .exchange(e.getExchange())
                .timestamp(e.getTimestamp())
                .fromCache(false)
                .build());
    }

    // ---- Historical OHLC ----

    public List<OHLCBarEntity> getHistory(String symbol, Instant from, Instant to) {
        return ohlcRepository.findBySymbolAndPeriod(symbol.toUpperCase().trim(), from, to);
    }

    // ---- Metrics ----

    public CacheMetricsResponse getMetrics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double ratio = total > 0 ? (double) hits / total : 0.0;
        return CacheMetricsResponse.builder()
                .cacheHits(hits)
                .cacheMisses(misses)
                .totalRequests(total)
                .hitRatio(ratio)
                .cachedSymbols(redisService.countCachedSymbols())
                .build();
    }

    /**
     * Pipeline latency = time from event.timestamp (when producer created it)
     * to now (when we read it from Redis). This covers the full path:
     * Producer → Kafka → Consumer → Redis → API read
     */
    public PipelineMetricsResponse getPipelineMetrics() {
        Instant now = Instant.now();
        Map<String, String> allPrices = redisService.scanAllPrices();

        List<PipelineMetricsResponse.SymbolLatency> symbolLatencies = new ArrayList<>();

        for (Map.Entry<String, String> entry : allPrices.entrySet()) {
            String symbol = entry.getKey();
            String raw = entry.getValue();
            try {
                String[] parts = raw.split("\\|");
                if (parts.length < 5)
                    continue;

                long eventEpochMs = Long.parseLong(parts[4]);
                long ageMs = now.toEpochMilli() - eventEpochMs;

                String syncStatus = ageMs < 2_000 ? "FRESH"
                        : ageMs < 10_000 ? "OK"
                                : "STALE";

                symbolLatencies.add(PipelineMetricsResponse.SymbolLatency.builder()
                        .symbol(symbol)
                        .eventType(parts[3])
                        .lastPrice(parts[0])
                        .eventTimestamp(Instant.ofEpochMilli(eventEpochMs).toString())
                        .ageMs(ageMs)
                        .syncStatus(syncStatus)
                        .build());
            } catch (Exception e) {
                log.warn("Skipping symbol {} — failed to parse Redis value: {}", symbol, raw);
            }
        }

        // Sort by symbol for readability
        symbolLatencies.sort(Comparator.comparing(PipelineMetricsResponse.SymbolLatency::getSymbol));

        if (symbolLatencies.isEmpty()) {
            return PipelineMetricsResponse.builder()
                    .checkedAt(now.toString())
                    .status("NO_DATA")
                    .cachedSymbols(0)
                    .minLatencyMs(0).maxLatencyMs(0).avgLatencyMs(0)
                    .symbols(List.of())
                    .build();
        }

        long minMs = symbolLatencies.stream().mapToLong(PipelineMetricsResponse.SymbolLatency::getAgeMs).min()
                .orElse(0);
        long maxMs = symbolLatencies.stream().mapToLong(PipelineMetricsResponse.SymbolLatency::getAgeMs).max()
                .orElse(0);
        long avgMs = (long) symbolLatencies.stream().mapToLong(PipelineMetricsResponse.SymbolLatency::getAgeMs)
                .average().orElse(0);

        String overallStatus = maxMs < 2_000 ? "IN_SYNC"
                : maxMs < 10_000 ? "LAGGING"
                        : "STALE";

        return PipelineMetricsResponse.builder()
                .checkedAt(now.toString())
                .status(overallStatus)
                .cachedSymbols(symbolLatencies.size())
                .minLatencyMs(minMs)
                .maxLatencyMs(maxMs)
                .avgLatencyMs(avgMs)
                .symbols(symbolLatencies)
                .build();
    }

    // ---- Helpers ----

    /**
     * Cache value format: "price|volume|exchange|eventType|timestampEpochMs"
     * Kept as a compact pipe-delimited string to minimize Redis memory usage.
     */
    private String buildCacheValue(BigDecimal price, BigDecimal volume,
            String exchange, EventType type, Instant ts) {
        return price.toPlainString()
                + "|" + (volume != null ? volume.toPlainString() : "0")
                + "|" + (exchange != null ? exchange : "UNKNOWN")
                + "|" + (type != null ? type.name() : "STOCK")
                + "|" + (ts != null ? ts.toEpochMilli() : Instant.now().toEpochMilli());
    }

    private PriceResponse parseRedisValue(String symbol, String raw, boolean fromCache) {
        try {
            String[] parts = raw.split("\\|");
            return PriceResponse.builder()
                    .symbol(symbol)
                    .price(new BigDecimal(parts[0]))
                    .volume(parts.length > 1 ? new BigDecimal(parts[1]) : BigDecimal.ZERO)
                    .exchange(parts.length > 2 ? parts[2] : "UNKNOWN")
                    .eventType(parts.length > 3 ? EventType.valueOf(parts[3]) : EventType.STOCK)
                    .timestamp(parts.length > 4 ? Instant.ofEpochMilli(Long.parseLong(parts[4])) : Instant.now())
                    .fromCache(fromCache)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Redis value for symbol={}: {}", symbol, raw);
            // Evict corrupt cache entry and force DB fallback
            redisService.delete(symbol);
            return getLatestPrice(symbol).orElse(null);
        }
    }
}
