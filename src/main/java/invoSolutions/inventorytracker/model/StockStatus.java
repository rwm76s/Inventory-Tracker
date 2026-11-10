package invoSolutions.inventorytracker.model;

/**
 * Derived, never stored: availability is a function of on-hand quantity and the
 * product's reorder point, so it can't drift out of sync with the ledger.
 */
public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK;

    public static StockStatus of(long onHand, int reorderPoint) {
        if (onHand <= 0) return OUT_OF_STOCK;
        if (onHand <= reorderPoint) return LOW_STOCK;
        return IN_STOCK;
    }
}