package com.marketstream.consumer.transformer;

import com.marketstream.common.model.EventType;
import com.marketstream.common.model.MarketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValidationTransformer Unit Tests")
class ValidationTransformerTest {

    private ValidationTransformer validator;

    @BeforeEach
    void setUp() {
        validator = new ValidationTransformer();
    }

    @Test
    @DisplayName("Valid event passes validation")
    void validEvent_passes() {
        MarketEvent event = validEvent();
        Optional<MarketEvent> result = validator.validate(event);
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Null event is rejected")
    void nullEvent_rejected() {
        Optional<MarketEvent> result = validator.validate(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Null symbol is rejected")
    void nullSymbol_rejected() {
        MarketEvent event = validEvent();
        event.setSymbol(null);
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Blank symbol is rejected")
    void blankSymbol_rejected() {
        MarketEvent event = validEvent();
        event.setSymbol("   ");
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Zero price is rejected")
    void zeroPrice_rejected() {
        MarketEvent event = validEvent();
        event.setPrice(BigDecimal.ZERO);
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Negative price is rejected")
    void negativePrice_rejected() {
        MarketEvent event = validEvent();
        event.setPrice(new BigDecimal("-1.00"));
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Null price is rejected")
    void nullPrice_rejected() {
        MarketEvent event = validEvent();
        event.setPrice(null);
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Null eventType is rejected")
    void nullEventType_rejected() {
        MarketEvent event = validEvent();
        event.setEventType(null);
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Null timestamp is rejected")
    void nullTimestamp_rejected() {
        MarketEvent event = validEvent();
        event.setTimestamp(null);
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Event older than 60 seconds is rejected")
    void staleEvent_rejected() {
        MarketEvent event = validEvent();
        event.setTimestamp(Instant.now().minusSeconds(61));
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Event more than 5 seconds in the future is rejected")
    void futureEvent_rejected() {
        MarketEvent event = validEvent();
        event.setTimestamp(Instant.now().plusSeconds(10));
        assertThat(validator.validate(event)).isEmpty();
    }

    @Test
    @DisplayName("Event 59 seconds old still passes")
    void slightlyOldEvent_passes() {
        MarketEvent event = validEvent();
        event.setTimestamp(Instant.now().minusSeconds(59));
        assertThat(validator.validate(event)).isPresent();
    }

    // Helper
    private MarketEvent validEvent() {
        return MarketEvent.builder()
                .symbol("AAPL")
                .eventType(EventType.STOCK)
                .price(new BigDecimal("175.50"))
                .volume(new BigDecimal("1000.00"))
                .exchange("NASDAQ")
                .timestamp(Instant.now())
                .ingestionTimestamp(Instant.now())
                .build();
    }
}
