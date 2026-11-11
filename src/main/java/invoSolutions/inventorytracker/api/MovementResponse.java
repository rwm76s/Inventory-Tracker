package invoSolutions.inventorytracker.api;

import invoSolutions.inventorytracker.model.MovementReason;
import invoSolutions.inventorytracker.model.MovementType;
import invoSolutions.inventorytracker.model.StockMovement;

import java.time.Instant;

public record MovementResponse(Long id, String reference, MovementType type, int quantityDelta,
                               MovementReason reason, String note, Instant occurredAt,
                               Instant createdAt, String createdBy) {
    static MovementResponse from(StockMovement m) {
        return new MovementResponse(m.getId(), m.getReference(), m.getType(), m.getQuantityDelta(),
                m.getReason(), m.getNote(), m.getOccurredAt(), m.getCreatedAt(), m.getCreatedBy());
    }
}
