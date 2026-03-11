package com.marketstream.producer.generator;

import com.marketstream.common.model.MarketEvent;
import com.marketstream.producer.service.KafkaProducerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDataGenerator Unit Tests")
class MarketDataGeneratorTest {

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Test
    @DisplayName("Generator is disabled when producer.enabled=false and sends nothing")
    void generatorDisabled_sendsNothing() {
        MarketDataGenerator generator = new MarketDataGenerator(kafkaProducerService);
        ReflectionTestUtils.setField(generator, "enabled", false);
        ReflectionTestUtils.setField(generator, "threadCount", 1);

        generator.start();

        verifyNoInteractions(kafkaProducerService);
        assertThat(generator.getTotalPublished()).isZero();
    }

    @Test
    @DisplayName("All generated events have non-null symbol")
    void generatedEvent_symbolNotNull() throws InterruptedException {
        MarketDataGenerator generator = new MarketDataGenerator(kafkaProducerService);
        ReflectionTestUtils.setField(generator, "enabled", true);
        ReflectionTestUtils.setField(generator, "threadCount", 1);

        generator.start();
        Thread.sleep(200); // Let it generate a few events
        generator.stop();

        ArgumentCaptor<MarketEvent> captor = ArgumentCaptor.forClass(MarketEvent.class);
        verify(kafkaProducerService, atLeastOnce()).send(captor.capture());

        List<MarketEvent> events = captor.getAllValues();
        assertThat(events).isNotEmpty();
        events.forEach(e -> {
            assertThat(e.getSymbol()).isNotBlank();
            assertThat(e.getPrice()).isPositive();
            assertThat(e.getEventType()).isNotNull();
            assertThat(e.getTimestamp()).isNotNull();
        });
    }

    @Test
    @DisplayName("Generated events have price > 0")
    void generatedEvent_pricePositive() throws InterruptedException {
        MarketDataGenerator generator = new MarketDataGenerator(kafkaProducerService);
        ReflectionTestUtils.setField(generator, "enabled", true);
        ReflectionTestUtils.setField(generator, "threadCount", 1);

        generator.start();
        Thread.sleep(100);
        generator.stop();

        ArgumentCaptor<MarketEvent> captor = ArgumentCaptor.forClass(MarketEvent.class);
        verify(kafkaProducerService, atLeastOnce()).send(captor.capture());
        captor.getAllValues().forEach(e -> assertThat(e.getPrice()).isPositive());
    }

    @Test
    @DisplayName("Generated events have recent timestamp (within 5 seconds)")
    void generatedEvent_recentTimestamp() throws InterruptedException {
        MarketDataGenerator generator = new MarketDataGenerator(kafkaProducerService);
        ReflectionTestUtils.setField(generator, "enabled", true);
        ReflectionTestUtils.setField(generator, "threadCount", 1);

        Instant before = Instant.now();
        generator.start();
        Thread.sleep(100);
        generator.stop();
        Instant after = Instant.now().plusSeconds(1);

        ArgumentCaptor<MarketEvent> captor = ArgumentCaptor.forClass(MarketEvent.class);
        verify(kafkaProducerService, atLeastOnce()).send(captor.capture());
        captor.getAllValues().forEach(e -> assertThat(e.getTimestamp()).isBetween(before, after));
    }

    @Test
    @DisplayName("totalPublished counter tracks events sent")
    void totalPublished_tracksEventCount() throws InterruptedException {
        MarketDataGenerator generator = new MarketDataGenerator(kafkaProducerService);
        ReflectionTestUtils.setField(generator, "enabled", true);
        ReflectionTestUtils.setField(generator, "threadCount", 1);

        generator.start();
        Thread.sleep(150);
        generator.stop();

        assertThat(generator.getTotalPublished()).isPositive();
    }
}
