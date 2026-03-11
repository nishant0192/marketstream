package com.marketstream.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cache performance metrics exposed by the /api/metrics/cache endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheMetricsResponse {

    private long cacheHits;
    private long cacheMisses;
    private long totalRequests;
    private double hitRatio;
    private long cachedSymbols;
}
