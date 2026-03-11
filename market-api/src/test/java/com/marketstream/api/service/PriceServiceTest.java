package com.marketstream.api.service;

import com.marketstream.api.repository.ApiMarketEventRepository;
import com.marketstream.api.repository.ApiOHLCBarRepository;
import com.marketstream.common.dto.CacheMetricsResponse;
import com.marketstream.common.dto.PriceResponse;
import com.marketstream.common.model.EventType;
import com.marketstream.common.entity.MarketEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceService Unit Tests")
class PriceServiceTest {

    @Mock
    private RedisService redisService;
    @Mock
    private ApiMarketEventRepository eventRepository;
    @Mock
    private ApiOHLCBarRepository ohlcRepository;

    private PriceService priceService;

    @BeforeEach
    void setUp() {
        priceService = new PriceService(redisService, eventRepository, ohlcRepository);
    }

    @Test
    @DisplayName("Cache HIT returns cached value without DB call")
    void cacheHit_returnsCachedValue_noDatabaseCall() {
        String cachedValue = "175.500000|1000.00|NASDAQ|STOCK|" + Instant.now().toEpochMilli();
        when(redisService.get("AAPL")).thenReturn(Optional.of(cachedValue));

        Optional<PriceResponse> result = priceService.getLatestPrice("AAPL");

        assertThat(result).isPresent();
        assertThat(result.get().isFromCache()).isTrue();
        assertThat(result.get().getSymbol()).isEqualTo("AAPL");
        assertThat(result.get().getPrice()).isEqualByComparingTo("175.500000");
        // DB must NOT be called on cache hit
        verifyNoInteractions(eventRepository);
    }

    @Test
    @DisplayName("Cache MISS queries PostgreSQL and populates cache")
    void cacheMiss_queriesPostgreSQL_populatesCache() {
        when(redisService.get("AAPL")).thenReturn(Optional.empty());

        MarketEventEntity entity = MarketEventEntity.builder()
                .symbol("AAPL").eventType(EventType.STOCK)
                .price(new BigDecimal("175.500000")).volume(new BigDecimal("1000.00"))
                .exchange("NASDAQ").timestamp(Instant.now()).build();
        when(eventRepository.findLatestBySymbol("AAPL")).thenReturn(Optional.of(entity));

        Optional<PriceResponse> result = priceService.getLatestPrice("AAPL");

        assertThat(result).isPresent();
        assertThat(result.get().isFromCache()).isFalse();
        assertThat(result.get().getPrice()).isEqualByComparingTo("175.500000");
        // Cache must be populated after miss
        verify(redisService).set(eq("AAPL"), anyString());
    }

    @Test
    @DisplayName("Cache MISS with no DB result returns empty")
    void cacheMiss_noDBResult_returnsEmpty() {
        when(redisService.get("UNKNOWN")).thenReturn(Optional.empty());
        when(eventRepository.findLatestBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        Optional<PriceResponse> result = priceService.getLatestPrice("UNKNOWN");

        assertThat(result).isEmpty();
        verify(redisService, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("Hit counter increments on cache hit")
    void hitCounter_incrementsOnCacheHit() {
        String cachedValue = "175.500000|1000.00|NASDAQ|STOCK|" + Instant.now().toEpochMilli();
        when(redisService.get("AAPL")).thenReturn(Optional.of(cachedValue));

        priceService.getLatestPrice("AAPL");
        priceService.getLatestPrice("AAPL");
        priceService.getLatestPrice("AAPL");

        CacheMetricsResponse metrics = priceService.getMetrics();
        assertThat(metrics.getCacheHits()).isEqualTo(3);
        assertThat(metrics.getCacheMisses()).isZero();
    }

    @Test
    @DisplayName("Miss counter increments on cache miss")
    void missCounter_incrementsOnCacheMiss() {
        when(redisService.get(anyString())).thenReturn(Optional.empty());
        when(eventRepository.findLatestBySymbol(anyString())).thenReturn(Optional.empty());

        priceService.getLatestPrice("AAPL");
        priceService.getLatestPrice("MSFT");

        CacheMetricsResponse metrics = priceService.getMetrics();
        assertThat(metrics.getCacheMisses()).isEqualTo(2);
        assertThat(metrics.getCacheHits()).isZero();
    }

    @Test
    @DisplayName("Hit ratio is calculated correctly")
    void hitRatio_calculatedCorrectly() {
        String cachedValue = "175.500000|1000.00|NASDAQ|STOCK|" + Instant.now().toEpochMilli();
        // 3 hits
        when(redisService.get("AAPL")).thenReturn(Optional.of(cachedValue));
        priceService.getLatestPrice("AAPL");
        priceService.getLatestPrice("AAPL");
        priceService.getLatestPrice("AAPL");
        // 1 miss
        when(redisService.get("MSFT")).thenReturn(Optional.empty());
        when(eventRepository.findLatestBySymbol("MSFT")).thenReturn(Optional.empty());
        priceService.getLatestPrice("MSFT");

        CacheMetricsResponse metrics = priceService.getMetrics();
        assertThat(metrics.getHitRatio()).isEqualTo(0.75);
        assertThat(metrics.getTotalRequests()).isEqualTo(4);
    }

    @Test
    @DisplayName("Symbol lookup is case-insensitive (lowercased input works)")
    void symbolLookup_caseInsensitive() {
        String cachedValue = "175.500000|1000.00|NASDAQ|STOCK|" + Instant.now().toEpochMilli();
        when(redisService.get("AAPL")).thenReturn(Optional.of(cachedValue));

        Optional<PriceResponse> result = priceService.getLatestPrice("aapl");
        assertThat(result).isPresent();
        assertThat(result.get().getSymbol()).isEqualTo("AAPL");
    }
}
