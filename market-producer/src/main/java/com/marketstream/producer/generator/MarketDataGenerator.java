package com.marketstream.producer.generator;

import com.marketstream.common.model.EventType;
import com.marketstream.common.model.MarketEvent;
import com.marketstream.producer.service.KafkaProducerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput mock market data generator.
 *
 * Uses 4 virtual threads (or platform threads pre-Java-21) to saturate Kafka
 * with realistic random-walk price data across stocks, forex, and crypto.
 * Target: 50,000+ events/second total across all symbols.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataGenerator {

    private final KafkaProducerService producerService;

    @Value("${producer.threads:4}")
    private int threadCount;

    @Value("${producer.enabled:true}")
    private boolean enabled;

    // ---- Symbol universe ----
    private static final List<String> STOCK_SYMBOLS = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META", "NFLX",
            "JPM", "BAC", "GS", "MS", "V", "MA", "PYPL", "WMT", "COST");
    private static final List<String> FOREX_SYMBOLS = List.of(
            "EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD", "USD/CAD", "NZD/USD");
    private static final List<String> CRYPTO_SYMBOLS = List.of(
            "BTC-USDT", "ETH-USDT", "BNB-USDT", "SOL-USDT", "ADA-USDT",
            "XRP-USDT", "DOT-USDT", "MATIC-USDT");

    // ---- Seed prices for realistic random walk ----
    private static final Map<String, Double> BASE_PRICES = new ConcurrentHashMap<>(Map.ofEntries(
            Map.entry("AAPL", 175.0), Map.entry("MSFT", 380.0), Map.entry("GOOGL", 140.0),
            Map.entry("AMZN", 180.0), Map.entry("NVDA", 850.0), Map.entry("TSLA", 200.0),
            Map.entry("META", 480.0), Map.entry("NFLX", 600.0), Map.entry("JPM", 195.0),
            Map.entry("BAC", 35.0), Map.entry("GS", 420.0), Map.entry("MS", 90.0),
            Map.entry("V", 270.0), Map.entry("MA", 450.0), Map.entry("PYPL", 65.0),
            Map.entry("WMT", 175.0), Map.entry("COST", 760.0),
            Map.entry("EUR/USD", 1.08), Map.entry("GBP/USD", 1.27), Map.entry("USD/JPY", 149.0),
            Map.entry("USD/CHF", 0.90), Map.entry("AUD/USD", 0.65), Map.entry("USD/CAD", 1.35),
            Map.entry("NZD/USD", 0.61),
            Map.entry("BTC-USDT", 52000.0), Map.entry("ETH-USDT", 3100.0), Map.entry("BNB-USDT", 420.0),
            Map.entry("SOL-USDT", 110.0), Map.entry("ADA-USDT", 0.60), Map.entry("XRP-USDT", 0.55),
            Map.entry("DOT-USDT", 8.0), Map.entry("MATIC-USDT", 0.95)));

    private static final Map<String, Double> CURRENT_PRICES = new ConcurrentHashMap<>(BASE_PRICES);
    private static final Map<String, String> EXCHANGES = Map.of(
            "STOCK", "NASDAQ", "FOREX", "FXCM", "CRYPTO", "BINANCE");

    private final AtomicLong totalPublished = new AtomicLong(0);
    private ExecutorService executorService;
    private volatile boolean running = false;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("MarketDataGenerator is disabled via configuration.");
            return;
        }
        running = true;
        executorService = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setName("market-gen-" + t.getId());
            return t;
        });
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(this::generateLoop);
        }
        log.info("MarketDataGenerator started with {} threads", threadCount);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (executorService != null) {
            executorService.shutdownNow();
        }
        log.info("MarketDataGenerator stopped. Total events published: {}", totalPublished.get());
    }

    private void generateLoop() {
        final Random random = new Random();
        final List<String>[] symbolGroups = new List[] { STOCK_SYMBOLS, FOREX_SYMBOLS, CRYPTO_SYMBOLS };
        final EventType[] types = EventType.values();
        int groupIdx = 0;

        while (running) {
            try {
                List<String> symbols = symbolGroups[groupIdx % 3];
                EventType type = types[groupIdx % 3];
                groupIdx++;

                for (String symbol : symbols) {
                    if (!running)
                        break;
                    MarketEvent event = generateEvent(symbol, type, random);
                    producerService.send(event);
                    long total = totalPublished.incrementAndGet();
                    if (total % 1000 == 0) {
                        log.info("Published {} events total", total);
                    }
                }

                // Brief pause between batches to avoid saturating Kafka send buffer
                Thread.sleep(10);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Generator thread interrupted, stopping");
                break;
            } catch (Exception e) {
                log.error("Error in market data generator loop: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private MarketEvent generateEvent(String symbol, EventType type, Random random) {
        // Random walk: ±0.05% change per tick (realistic HFT-scale movement)
        double current = CURRENT_PRICES.getOrDefault(symbol, 100.0);
        double change = current * (random.nextGaussian() * 0.0005);
        double newPrice = Math.max(0.0001, current + change);
        CURRENT_PRICES.put(symbol, newPrice);

        double volume = 100 + random.nextInt(10000);
        String exchange = EXCHANGES.getOrDefault(type.name(), "UNKNOWN");

        return MarketEvent.builder()
                .symbol(symbol)
                .eventType(type)
                .price(BigDecimal.valueOf(newPrice).setScale(6, RoundingMode.HALF_UP))
                .volume(BigDecimal.valueOf(volume).setScale(2, RoundingMode.HALF_UP))
                .exchange(exchange)
                .timestamp(Instant.now())
                .ingestionTimestamp(Instant.now())
                .build();
    }

    public long getTotalPublished() {
        return totalPublished.get();
    }
}
