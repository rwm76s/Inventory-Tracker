package invoSolutions.inventorytracker.model;

/**
 * Short, reportable reason code. Free-text detail goes in StockMovement.note.
 * An enum (not a free string) so reasons can be grouped and counted later.
 */
public enum MovementReason {
    SUPPLIER_DELIVERY,
    CUSTOMER_RETURN,
    SALE,
    DAMAGED,
    LOST_OR_STOLEN,
    RECOUNT,
    INITIAL_STOCK,
    OTHER
}