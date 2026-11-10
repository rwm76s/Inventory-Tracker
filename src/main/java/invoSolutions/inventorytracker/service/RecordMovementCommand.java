package invoSolutions.inventorytracker.service;

import invoSolutions.inventorytracker.model.MovementReason;
import invoSolutions.inventorytracker.model.MovementType;

import java.time.Instant;

public record RecordMovementCommand(
        Long productId,
        MovementType type,
        int quantityDelta,   // signed, same convention as the ledger; the UI layer maps "issue 5" to -5
        MovementReason reason,
        String note,
        Instant occurredAt,  // null = now
        String createdBy) { }
