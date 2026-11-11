package invoSolutions.inventorytracker.api;

import invoSolutions.inventorytracker.model.Product;
import invoSolutions.inventorytracker.model.StockStatus;
import invoSolutions.inventorytracker.service.ProductView;

import java.math.BigDecimal;
import java.time.Instant;

/** What the API returns. Entities never cross the HTTP boundary, so the JSON shape can't leak schema changes. */
public record ProductResponse(Long id, String sku, String name, String description, int reorderThreshold,
                              BigDecimal unitCost, boolean active, long onHand, StockStatus status,
                              Instant createdAt) {
    static ProductResponse from(ProductView v) {
        Product p = v.product();
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getReorderThreshold(), p.getUnitCost(), p.isActive(), v.onHand(), v.status(),
                p.getCreatedAt());
    }
}
