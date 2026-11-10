package invoSolutions.inventorytracker.exceptions;

/** The request itself is malformed (bad sign, future date, missing reason). */
public class InvalidMovementException extends RuntimeException {
    public InvalidMovementException(String message) { super(message); }
}
