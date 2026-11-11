package invoSolutions.inventorytracker.service;

import invoSolutions.inventorytracker.model.Product;
import invoSolutions.inventorytracker.model.StockStatus;

/** A product plus its derived stock figures; status is computed, never stored. */
public record ProductView(Product product, long onHand) {
    public StockStatus status() { return StockStatus.of(onHand, product.getReorderThreshold()); }
}
