package com.marketstream.consumer.transformer;

import com.marketstream.common.model.MarketEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * First stage in the ETL pipeline.
 * Rejects events that are null, have zero/negative prices,
 * missing symbols, or timestamps that are stale (>60s old) or future (>5s
 * ahead).
 */
@Slf4j
@Component
public class ValidationTransformer {

    private static final int MAX_STALENESS_SECONDS = 60;
    private static final int MAX_FUTURE_SECONDS = 5;

    /**
     * @return the event wrapped in Optional if valid, empty Optional if rejected.
     */
    public Optional<MarketEvent> validate(MarketEvent event) {
        if (event == null) {
            log.warn("Rejected null event");
            return Optional.empty();
        }
        if (event.getSymbol() == null || event.getSymbol().isBlank()) {
            log.warn("Rejected event with null/blank symbol");
            return Optional.empty();
        }
        if (event.getPrice() == null || event.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Rejected event symbol={} with invalid price={}", event.getSymbol(), event.getPrice());
            return Optional.empty();
        }
        if (event.getEventType() == null) {
            log.warn("Rejected event symbol={} with null eventType", event.getSymbol());
            return Optional.empty();
        }
        if (event.getTimestamp() == null) {
            log.warn("Rejected event symbol={} with null timestamp", event.getSymbol());
            return Optional.empty();
        }

        Instant now = Instant.now();
        long secondsOld = ChronoUnit.SECONDS.between(event.getTimestamp(), now);
        if (secondsOld > MAX_STALENESS_SECONDS) {
            log.warn("Rejected stale event symbol={} age={}s", event.getSymbol(), secondsOld);
            return Optional.empty();
        }
        if (secondsOld < -MAX_FUTURE_SECONDS) {
            log.warn("Rejected future-dated event symbol={} drift={}s", event.getSymbol(), -secondsOld);
            return Optional.empty();
        }

        return Optional.of(event);
    }
}
