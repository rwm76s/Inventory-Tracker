package invoSolutions.inventorytracker.exceptions;

/** The request is well-formed but would drive stock negative at some point in history. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) { super(message); }
}
