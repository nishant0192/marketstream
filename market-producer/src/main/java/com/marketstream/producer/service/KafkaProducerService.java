package com.marketstream.producer.service;

import com.marketstream.common.model.MarketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Wraps KafkaTemplate with async sends and error logging.
 * The symbol is used as the partition key to ensure ordering
 * per instrument and balanced partition distribution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final String STOCKS_TOPIC = "market.stocks";
    private static final String FOREX_TOPIC = "market.forex";
    private static final String CRYPTO_TOPIC = "market.crypto";

    private final KafkaTemplate<String, MarketEvent> kafkaTemplate;

    public void send(MarketEvent event) {
        String topic = resolveTopic(event);
        CompletableFuture<SendResult<String, MarketEvent>> future = kafkaTemplate.send(topic, event.getSymbol(), event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send event for symbol={} to topic={}: {}",
                        event.getSymbol(), topic, ex.getMessage());
            }
        });
    }

    private String resolveTopic(MarketEvent event) {
        return switch (event.getEventType()) {
            case STOCK -> STOCKS_TOPIC;
            case FOREX -> FOREX_TOPIC;
            case CRYPTO -> CRYPTO_TOPIC;
        };
    }
}
