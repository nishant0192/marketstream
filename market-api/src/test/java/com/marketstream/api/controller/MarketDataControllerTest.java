package com.marketstream.api.controller;

import com.marketstream.api.service.PriceService;
import com.marketstream.common.dto.CacheMetricsResponse;
import com.marketstream.common.dto.PriceResponse;
import com.marketstream.common.model.EventType;
import com.marketstream.common.entity.OHLCBarEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketDataController.class)
@DisplayName("MarketDataController Integration Tests")
class MarketDataControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private PriceService priceService;

        // ---- /api/prices/{symbol} ----

        @Test
        @DisplayName("GET /api/prices/AAPL returns 200 for known symbol")
        void getLatestPrice_knownSymbol_returns200() throws Exception {
                PriceResponse response = PriceResponse.builder()
                                .symbol("AAPL").eventType(EventType.STOCK)
                                .price(new BigDecimal("175.500000")).volume(new BigDecimal("1000.00"))
                                .exchange("NASDAQ").timestamp(Instant.parse("2024-01-15T10:30:00Z"))
                                .fromCache(true).build();
                when(priceService.getLatestPrice("AAPL")).thenReturn(Optional.of(response));

                mockMvc.perform(get("/api/prices/AAPL").accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.symbol").value("AAPL"))
                                .andExpect(jsonPath("$.price").value(175.5))
                                .andExpect(jsonPath("$.fromCache").value(true))
                                .andExpect(jsonPath("$.eventType").value("STOCK"));
        }

        @Test
        @DisplayName("GET /api/prices/UNKNOWN returns 404 for unknown symbol")
        void getLatestPrice_unknownSymbol_returns404() throws Exception {
                when(priceService.getLatestPrice("UNKNOWN")).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/prices/UNKNOWN").accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound());
        }

        // ---- /api/prices/{symbol}/history ----

        @Test
        @DisplayName("GET /api/prices/AAPL/history returns 200 with OHLC bars")
        void getHistory_returnsOHLCBars() throws Exception {
                OHLCBarEntity bar = OHLCBarEntity.builder()
                                .symbol("AAPL").eventType(EventType.STOCK)
                                .open(new BigDecimal("150.00")).high(new BigDecimal("155.00"))
                                .low(new BigDecimal("149.00")).close(new BigDecimal("153.00"))
                                .volume(new BigDecimal("10000.00"))
                                .periodStart(Instant.parse("2024-01-15T10:00:00Z"))
                                .periodEnd(Instant.parse("2024-01-15T10:01:00Z"))
                                .tickCount(250).build();

                when(priceService.getHistory(eq("AAPL"), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.of(bar));

                mockMvc.perform(get("/api/prices/AAPL/history")
                                .param("from", "2024-01-15T10:00:00Z")
                                .param("to", "2024-01-15T11:00:00Z")
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                                .andExpect(jsonPath("$[0].open").value(150.0))
                                .andExpect(jsonPath("$[0].close").value(153.0));
        }

        @Test
        @DisplayName("GET /api/prices/AAPL/history returns 204 when no bars exist")
        void getHistory_noBars_returns204() throws Exception {
                when(priceService.getHistory(anyString(), any(Instant.class), any(Instant.class)))
                                .thenReturn(List.of());

                mockMvc.perform(get("/api/prices/AAPL/history")
                                .param("from", "2024-01-15T10:00:00Z")
                                .param("to", "2024-01-15T11:00:00Z"))
                                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("GET /api/prices/AAPL/history with from > to returns 400")
        void getHistory_fromAfterTo_returns400() throws Exception {
                mockMvc.perform(get("/api/prices/AAPL/history")
                                .param("from", "2024-01-15T11:00:00Z")
                                .param("to", "2024-01-15T10:00:00Z"))
                                .andExpect(status().isBadRequest());
        }

        // ---- /api/metrics/cache ----

        @Test
        @DisplayName("GET /api/metrics/cache returns hit/miss counters")
        void getCacheMetrics_returnsMetrics() throws Exception {
                CacheMetricsResponse metrics = CacheMetricsResponse.builder()
                                .cacheHits(750).cacheMisses(250).totalRequests(1000)
                                .hitRatio(0.75).cachedSymbols(32).build();
                when(priceService.getMetrics()).thenReturn(metrics);

                mockMvc.perform(get("/api/metrics/cache").accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.cacheHits").value(750))
                                .andExpect(jsonPath("$.cacheMisses").value(250))
                                .andExpect(jsonPath("$.hitRatio").value(0.75))
                                .andExpect(jsonPath("$.cachedSymbols").value(32));
        }

        // ---- /api/health ----

        @Test
        @DisplayName("GET /api/health returns 200 UP")
        void health_returns200() throws Exception {
                mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("UP"));
        }
}
