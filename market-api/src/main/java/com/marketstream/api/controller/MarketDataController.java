package com.marketstream.api.controller;

import com.marketstream.api.service.PriceService;
import com.marketstream.common.dto.CacheMetricsResponse;
import com.marketstream.common.dto.PriceResponse;
import com.marketstream.common.entity.OHLCBarEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST API for MarketStream.
 *
 * Endpoints:
 * GET /api/prices/{symbol} — latest tick (Redis → PostgreSQL fallback)
 * GET /api/prices/{symbol}/history — OHLC bars in a time range
 * GET /api/metrics/cache — hit/miss counters and ratio
 * GET /api/health — simple liveness check
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MarketDataController {

    private final PriceService priceService;

    /**
     * Latest price for a symbol.
     * Returns 200 with PriceResponse (fromCache=true if served from Redis).
     * Returns 404 if symbol has no data yet.
     */
    @GetMapping("/prices/{symbol}")
    public ResponseEntity<PriceResponse> getLatestPrice(@PathVariable String symbol) {
        return priceService.getLatestPrice(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Historical OHLC bars for a symbol.
     * 
     * @param from ISO-8601 start instant (e.g. 2024-01-01T00:00:00Z)
     * @param to   ISO-8601 end instant
     */
    @GetMapping("/prices/{symbol}/history")
    public ResponseEntity<List<OHLCBarEntity>> getHistory(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        List<OHLCBarEntity> bars = priceService.getHistory(symbol, from, to);
        if (bars.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(bars);
    }

    /**
     * Cache performance metrics — used to demonstrate ≥60% cache hit ratio.
     */
    @GetMapping("/metrics/cache")
    public ResponseEntity<CacheMetricsResponse> getCacheMetrics() {
        return ResponseEntity.ok(priceService.getMetrics());
    }

    /**
     * Pipeline latency metrics — shows end-to-end event age per symbol.
     * Latency = event.timestamp → now, covering: Producer → Kafka → Consumer →
     * Redis
     * Status: IN_SYNC (<2s), LAGGING (<10s), STALE (>10s)
     */
    @GetMapping("/metrics/pipeline")
    public ResponseEntity<com.marketstream.common.dto.PipelineMetricsResponse> getPipelineMetrics() {
        return ResponseEntity.ok(priceService.getPipelineMetrics());
    }

    /** Simple liveness probe */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "market-api"));
    }
}
