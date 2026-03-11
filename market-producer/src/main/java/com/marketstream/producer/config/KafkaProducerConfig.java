package com.marketstream.producer.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import com.marketstream.common.model.MarketEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration optimized for high-throughput.
 * Key settings: large batch size, short linger, snappy compression.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, MarketEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Throughput optimizations
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536); // 64KB batch
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5); // 5ms linger to fill batch
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // Pure-Java compression, no native deps

        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864L); // 64MB buffer
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader ack only for throughput
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, MarketEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
