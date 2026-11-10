package invoSolutions.inventorytracker.exceptions;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) { super("No product with id " + id); }
}
