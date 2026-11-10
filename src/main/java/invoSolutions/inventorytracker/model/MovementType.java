package invoSolutions.inventorytracker.domain;

/**
 * What kind of stock movement this is. The type constrains the sign of the
 * quantity delta, which keeps the ledger honest (a "receipt" can never remove stock).
 */
public enum MovementType {
    RECEIPT,     // stock comes in  -> delta > 0
    ISSUE,       // stock goes out  -> delta < 0
    ADJUSTMENT;  // correction      -> delta != 0 (either direction)

    public boolean acceptsDelta(int delta) {
        return switch (this) {
            case RECEIPT -> delta > 0;
            case ISSUE -> delta < 0;
            case ADJUSTMENT -> delta != 0;
        };
    }
}