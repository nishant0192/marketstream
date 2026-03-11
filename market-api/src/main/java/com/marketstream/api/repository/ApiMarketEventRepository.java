package com.marketstream.api.repository;

import com.marketstream.common.entity.MarketEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * API-side repository for reading market event ticks from PostgreSQL.
 * Shares the same entity as market-consumer (common schema).
 */
@Repository
public interface ApiMarketEventRepository extends JpaRepository<MarketEventEntity, Long> {

        @Query("SELECT m FROM MarketEventEntity m WHERE m.symbol = :symbol ORDER BY m.timestamp DESC LIMIT 1")
        Optional<MarketEventEntity> findLatestBySymbol(@Param("symbol") String symbol);

        @Query("SELECT m FROM MarketEventEntity m WHERE m.symbol = :symbol " +
                        "AND m.timestamp BETWEEN :from AND :to ORDER BY m.timestamp ASC")
        List<MarketEventEntity> findBySymbolAndTimeRange(
                        @Param("symbol") String symbol,
                        @Param("from") Instant from,
                        @Param("to") Instant to);
}
