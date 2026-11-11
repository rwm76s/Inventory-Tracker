package invoSolutions.inventorytracker.model;

/**
 * Derived, never stored: availability is a function of on-hand quantity and the
 * product's reorder point, so it can't drift out of sync with the ledger.
 */
public enum StockStatus {
    IN_STOCK("In Stock"),
    LOW_STOCK("Low Stock"),
    OUT_OF_STOCK("Out of Stock");

    private final String label;

    StockStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }


    public static StockStatus of(long onHand, int reorderPoint) {
        if (onHand <= 0) return OUT_OF_STOCK;
        if (onHand <= reorderPoint) return LOW_STOCK;
        return IN_STOCK;
    }
}