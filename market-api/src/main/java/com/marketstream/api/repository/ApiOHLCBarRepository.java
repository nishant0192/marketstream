package com.marketstream.api.repository;

import com.marketstream.common.entity.OHLCBarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiOHLCBarRepository extends JpaRepository<OHLCBarEntity, Long> {

        @Query("SELECT o FROM OHLCBarEntity o WHERE o.symbol = :symbol " +
                        "AND o.periodStart >= :from AND o.periodEnd <= :to ORDER BY o.periodStart ASC")
        List<OHLCBarEntity> findBySymbolAndPeriod(
                        @Param("symbol") String symbol,
                        @Param("from") Instant from,
                        @Param("to") Instant to);

        @Query("SELECT o FROM OHLCBarEntity o WHERE o.symbol = :symbol ORDER BY o.periodStart DESC LIMIT 1")
        Optional<OHLCBarEntity> findLatestBySymbol(@Param("symbol") String symbol);
}
