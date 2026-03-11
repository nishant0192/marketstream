package com.marketstream.consumer.service;

import com.marketstream.common.model.MarketEvent;
import com.marketstream.consumer.transformer.NormalizationTransformer;
import com.marketstream.consumer.transformer.OHLCAggregator;
import com.marketstream.consumer.transformer.ValidationTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka consumer service wiring the ETL pipeline:
 * Validate → Normalize → Redis update → OHLC aggregate
 *
 * Raw tick Postgres writes are removed from the hot path.
 * OHLC bars are batch-flushed to Postgres every 30s by OHLCAggregator's
 * scheduler.
 *
 * concurrency=8: 8 consumer threads × 3 topics = up to 24 partitions consumed
 * in parallel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketEventConsumer {

    private final ValidationTransformer validator;
    private final NormalizationTransformer normalizer;
    private final OHLCAggregator aggregator;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_PRICE_KEY_PREFIX = "price:";
    private static final Duration REDIS_TTL = Duration.ofSeconds(30);

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);

    @KafkaListener(topics = { "market.stocks", "market.forex",
            "market.crypto" }, groupId = "${spring.kafka.consumer.group-id}", concurrency = "8" // increased from 4 → 8
                                                                                                // for higher throughput
    )
    public void consume(
            @Payload MarketEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        // Stage 1: Validate
        Optional<MarketEvent> validEvent = validator.validate(event);
        if (validEvent.isEmpty()) {
            rejectedCount.incrementAndGet();
            return;
        }

        // Stage 2: Normalize
        MarketEvent normalized = normalizer.normalize(validEvent.get());

        // Stage 3: Push latest price to Redis (fast in-memory write, no Postgres
        // round-trip)
        updateRedisCache(normalized);

        // Stage 4: Update OHLC aggregator (in-memory ConcurrentHashMap, no I/O)
        aggregator.ingest(normalized);

        // Raw tick Postgres save removed from hot path.
        // OHLC bars are batch-flushed to Postgres by OHLCAggregator every 30s.

        processedCount.incrementAndGet();
    }

    private void updateRedisCache(MarketEvent event) {
        String key = REDIS_PRICE_KEY_PREFIX + event.getSymbol();
        String value = event.getPrice().toPlainString()
                + "|" + event.getVolume().toPlainString()
                + "|" + event.getExchange()
                + "|" + event.getEventType().name()
                + "|" + event.getTimestamp().toEpochMilli();
        redisTemplate.opsForValue().set(key, value, REDIS_TTL);
    }

    public long getProcessedCount() {
        return processedCount.get();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }
}
