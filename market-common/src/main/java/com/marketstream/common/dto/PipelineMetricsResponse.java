package com.marketstream.common.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response from GET /api/metrics/pipeline
 *
 * Shows end-to-end latency (event timestamp → Redis write) and per-symbol sync
 * status.
 * Latency is computed as: Instant.now() - event.getTimestamp()
 * which covers: producer → Kafka → consumer → Redis
 */
@Data
@Builder
public class PipelineMetricsResponse {

    /** Current wall-clock time (ISO-8601) for reference */
    private String checkedAt;

    /** Overall sync status: "IN_SYNC", "LAGGING", or "STALE" */
    private String status;

    /** Number of symbols currently cached in Redis */
    private int cachedSymbols;

    /** Minimum event age across all cached symbols (ms) */
    private long minLatencyMs;

    /** Maximum event age across all cached symbols (ms) */
    private long maxLatencyMs;

    /** Average event age across all cached symbols (ms) */
    private long avgLatencyMs;

    /** Per-symbol breakdown */
    private List<SymbolLatency> symbols;

    @Data
    @Builder
    public static class SymbolLatency {
        private String symbol;
        private String eventType;
        private String lastPrice;
        private String eventTimestamp;
        private long ageMs; // how old the last event is
        private String syncStatus; // "FRESH" (<2s), "OK" (<10s), "STALE" (>10s)
    }
}
