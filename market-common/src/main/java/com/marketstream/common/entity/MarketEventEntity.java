package com.marketstream.common.entity;

import com.marketstream.common.model.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_events", indexes = {
        @Index(name = "idx_market_events_symbol_ts", columnList = "symbol, timestamp DESC"),
        @Index(name = "idx_market_events_event_type", columnList = "event_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "market_event_seq")
    @SequenceGenerator(name = "market_event_seq", sequenceName = "market_event_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 10)
    private EventType eventType;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal price;

    @Column(precision = 20, scale = 2)
    private BigDecimal volume;

    @Column(length = 32)
    private String exchange;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "ingestion_timestamp")
    private Instant ingestionTimestamp;

    @Column(name = "processing_latency_ms")
    private Long processingLatencyMs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
