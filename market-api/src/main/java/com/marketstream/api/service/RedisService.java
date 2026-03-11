package com.marketstream.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin wrapper around RedisTemplate.
 * All keys use the "price:{symbol}" convention shared with the consumer
 * write-through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "price:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    public Optional<String> get(String symbol) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + symbol);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Redis GET failed for symbol={}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    public void set(String symbol, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + symbol, value, ttl);
        } catch (Exception e) {
            log.warn("Redis SET failed for symbol={}: {}", symbol, e.getMessage());
        }
    }

    public void set(String symbol, String value) {
        set(symbol, value, DEFAULT_TTL);
    }

    public void delete(String symbol) {
        try {
            redisTemplate.delete(KEY_PREFIX + symbol);
        } catch (Exception e) {
            log.warn("Redis DEL failed for symbol={}: {}", symbol, e.getMessage());
        }
    }

    /** Count keys matching price:* pattern — used for cache metrics */
    public long countCachedSymbols() {
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("Redis KEYS failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Returns all cached price entries as symbol → raw-value map.
     * Raw value format: "price|volume|exchange|eventType|timestampMillis"
     * Used by the pipeline metrics endpoint to compute end-to-end latency.
     */
    public java.util.Map<String, String> scanAllPrices() {
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty())
                return java.util.Map.of();
            java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    // Strip "price:" prefix to get the symbol
                    result.put(key.substring(KEY_PREFIX.length()), value);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis scan failed: {}", e.getMessage());
            return java.util.Map.of();
        }
    }
}
