package invoSolutions.inventorytracker.service;

import invoSolutions.inventorytracker.exceptions.*;
import invoSolutions.inventorytracker.model.*;
import invoSolutions.inventorytracker.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class StockService {

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final Clock clock;

    public StockService(ProductRepository products, StockMovementRepository movements, Clock clock) {
        this.products = products;
        this.movements = movements;
        this.clock = clock;
    }

    @Transactional
    public StockMovement recordMovement(RecordMovementCommand cmd) {
        Instant now = clock.instant();
        Instant occurredAt = cmd.occurredAt() != null ? cmd.occurredAt() : now;

        // Cheap input checks first, so we never hold the row lock for a request that was doomed anyway.
        validate(cmd, occurredAt, now);

        // Every writer must come through here: the lock only serializes callers that also take it.
        Product product = products.findByIdForUpdate(cmd.productId())
                .orElseThrow(() -> new ProductNotFoundException(cmd.productId()));

        // Only removals can break the "never negative" rule; receipts can't lower any balance.
        if (cmd.quantityDelta() < 0) {
            assertNeverNegative(product.getId(), occurredAt, cmd.quantityDelta());
        }

        return movements.save(new StockMovement(product, cmd.type(), cmd.quantityDelta(),
                cmd.reason(), cmd.note(), occurredAt, cmd.createdBy()));
    }

    @Transactional
    public long onHand(Long productId) {
        return movements.onHand(productId);
    }

    @Transactional
    public StockStatus statusOf(Long productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return StockStatus.of(movements.onHand(productId), product.getReorderThreshold());
    }

    private void validate(RecordMovementCommand cmd, Instant occurredAt, Instant now) {
        if (cmd.productId() == null || cmd.type() == null || cmd.reason() == null) {
            throw new InvalidMovementException("Product, movement type and reason are required");
        }
        if (!cmd.type().acceptsDelta(cmd.quantityDelta())) {
            throw new InvalidMovementException(
                    cmd.type() + " movement cannot have quantity delta " + cmd.quantityDelta());
        }
        if (occurredAt.isAfter(now)) {
            throw new InvalidMovementException("A movement cannot occur in the future");
        }
        // "OTHER" is unreportable without detail, so make the note carry the meaning.
        if (cmd.reason() == MovementReason.OTHER && (cmd.note() == null || cmd.note().isBlank())) {
            throw new InvalidMovementException("A note is required when the reason is OTHER");
        }
    }

    /**
     * Replays the ledger forward from occurredAt with the new delta applied. Inserting into the
     * past lowers every later balance too, so checking only "then" and "now" would miss a dip
     * in between. Ties at the same instant count as already happened (matches onHandAsOf's <=).
     */
    private void assertNeverNegative(Long productId, Instant occurredAt, int delta) {
        long balance = movements.onHandAsOf(productId, occurredAt) + delta;
        if (balance < 0) {
            throw new InsufficientStockException("Only " + (balance - delta)
                    + " on hand as of " + occurredAt + "; cannot remove " + (-delta));
        }
        for (StockMovement later : movements
                .findByProductIdAndOccurredAtAfterOrderByOccurredAtAscIdAsc(productId, occurredAt)) {
            balance += later.getQuantityDelta();
            if (balance < 0) {
                throw new InsufficientStockException("This would leave stock at " + balance
                        + " after " + later.getReference() + " (" + later.getOccurredAt() + ")");
            }
        }
    }
}
