package invoSolutions.inventorytracker.repository;

import invoSolutions.inventorytracker.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Current on-hand: the sum of every delta ever recorded for the product. */
    @Query("select coalesce(sum(m.quantityDelta), 0L) from StockMovement m where m.product.id = :productId")
    long onHand(@Param("productId") Long productId);

    /** Historical on-hand: only movements that had occurred by the given instant. */
    @Query("""
           select coalesce(sum(m.quantityDelta), 0L)
           from StockMovement m
           where m.product.id = :productId and m.occurredAt <= :asOf
           """)
    long onHandAsOf(@Param("productId") Long productId, @Param("asOf") Instant asOf);

    /** Newest first; id breaks ties so ordering is stable for equal timestamps. */
    Page<StockMovement> findByProductIdOrderByOccurredAtDescIdDesc(Long productId, Pageable pageable);

    /** Everything strictly after the instant, in ledger order; used to replay balances forward. */
    List<StockMovement> findByProductIdAndOccurredAtAfterOrderByOccurredAtAscIdAsc(Long productId, Instant after);
}