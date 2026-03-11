package com.marketstream.consumer.repository;

import com.marketstream.common.entity.MarketEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketEventRepository extends JpaRepository<MarketEventEntity, Long> {

        /** Latest tick for a given symbol, used on cache miss */
        @Query("SELECT m FROM MarketEventEntity m WHERE m.symbol = :symbol ORDER BY m.timestamp DESC LIMIT 1")
        Optional<MarketEventEntity> findLatestBySymbol(@Param("symbol") String symbol);

        /** Ticks in a time range for a symbol */
        @Query("SELECT m FROM MarketEventEntity m WHERE m.symbol = :symbol " +
                        "AND m.timestamp BETWEEN :from AND :to ORDER BY m.timestamp ASC")
        List<MarketEventEntity> findBySymbolAndTimeRange(
                        @Param("symbol") String symbol,
                        @Param("from") Instant from,
                        @Param("to") Instant to);
}
