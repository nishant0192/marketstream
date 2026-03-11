package com.marketstream.common.entity;

import com.marketstream.common.model.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ohlc_bars", indexes = {
        @Index(name = "idx_ohlc_symbol_start", columnList = "symbol, period_start DESC"),
        @Index(name = "idx_ohlc_event_type", columnList = "event_type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_ohlc_symbol_period", columnNames = { "symbol", "period_start" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OHLCBarEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ohlc_seq")
    @SequenceGenerator(name = "ohlc_seq", sequenceName = "ohlc_seq", allocationSize = 20)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 10)
    private EventType eventType;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal open;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal high;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal low;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal close;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal volume;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "tick_count", nullable = false)
    private long tickCount;
}
