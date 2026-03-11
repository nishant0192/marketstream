package com.marketstream.common.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import com.marketstream.common.model.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * REST response DTO for latest price queries.
 * Includes whether data was served from cache or database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse {

    private String symbol;
    private EventType eventType;
    private BigDecimal price;
    private BigDecimal volume;
    private String exchange;

    @JsonSerialize(using = InstantSerializer.class)
    private Instant timestamp;

    /** True if this response was served from Redis cache */
    private boolean fromCache;
}
