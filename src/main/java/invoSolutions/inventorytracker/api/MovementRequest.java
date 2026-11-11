package invoSolutions.inventorytracker.api;

import invoSolutions.inventorytracker.model.MovementReason;
import invoSolutions.inventorytracker.model.MovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record MovementRequest(
        @NotNull MovementType type,
        @NotNull Integer quantityDelta,     // receipts +, fulfillments -, adjustments are either
        @NotNull MovementReason reason,
        @Size(max = 500) String note,       // matches the column length, so too-long is a 400, not a DB error
        Instant occurredAt,                 // omit for "now"
        @Size(max = 100) String createdBy) { } // omit and it's recorded as "system"
