package invoSolutions.inventorytracker.repository;

import invoSolutions.inventorytracker.model.MovementType;
import invoSolutions.inventorytracker.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    StockMovement save(StockMovement movement);

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

    /** Everything strictly after the instant, in ledger order; used to replay balances forward. */
    List<StockMovement> findByProductIdAndOccurredAtAfterOrderByOccurredAtAscIdAsc(Long productId, Instant after);

    /** One row per product, for a whole page of products at once (avoids a SUM query per product). */
    interface OnHandRow {
        Long getProductId();
        Long getOnHand();
    }

    @Query("""
       select m.product.id as productId, sum(m.quantityDelta) as onHand
       from StockMovement m
       where m.product.id in :ids
       group by m.product.id
       """)
    List<OnHandRow> onHandForProducts(@Param("ids") Collection<Long> ids);

    @Query("""
       select m from StockMovement m
       where m.product.id = :productId
         and m.occurredAt >= :from and m.occurredAt < :to
         and m.type in :types
       order by m.occurredAt desc, m.id desc
       """)
    Page<StockMovement> search(@Param("productId") Long productId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               @Param("types") Collection<MovementType> types,
                               Pageable pageable);
}