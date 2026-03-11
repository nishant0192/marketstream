package com.marketstream.consumer.repository;

import com.marketstream.common.entity.OHLCBarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OHLCBarRepository extends JpaRepository<OHLCBarEntity, Long> {

        /** OHLC bars for a symbol within a time range, ordered oldest-first */
        @Query("SELECT o FROM OHLCBarEntity o WHERE o.symbol = :symbol " +
                        "AND o.periodStart >= :from AND o.periodEnd <= :to ORDER BY o.periodStart ASC")
        List<OHLCBarEntity> findBySymbolAndPeriod(
                        @Param("symbol") String symbol,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        /** Most recent bar for a symbol */
        @Query("SELECT o FROM OHLCBarEntity o WHERE o.symbol = :symbol ORDER BY o.periodStart DESC LIMIT 1")
        java.util.Optional<OHLCBarEntity> findLatestBySymbol(@Param("symbol") String symbol);
}
