package invoSolutions.inventorytracker.config;

import invoSolutions.inventorytracker.model.*;
import invoSolutions.inventorytracker.repository.ProductRepository;
import invoSolutions.inventorytracker.service.RecordMovementCommand;
import invoSolutions.inventorytracker.service.StockService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Component
public class SeedData implements CommandLineRunner {

    private final ProductRepository products;
    private final StockService stock;
    private final Clock clock;

    public SeedData(ProductRepository products, StockService stock, Clock clock) {
        this.products = products;
        this.stock = stock;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        // Idempotent: restarts, and the persistent Postgres volume, never duplicate the data.
        if (products.count() > 0) return;

        // Each product is built oldest-to-newest, so a fulfillment never precedes its stock.
        Product widget = product("6579088", "Widget", 10, "4.50");      // ends at 18 -> IN_STOCK
        receive(widget, 100, MovementReason.INITIAL_STOCK, 30);
        sell(widget, 40, 20);
        sell(widget, 30, 10);
        sell(widget, 12, 3);

        Product gadget = product("6574343", "Gadget", 15, "12.00");     // ends at 12 -> LOW_STOCK
        receive(gadget, 50, MovementReason.INITIAL_STOCK, 30);
        sell(gadget, 20, 14);
        sell(gadget, 18, 5);

        Product gasket = product("6572318", "Gasket", 5, "0.75");       // ends at 0 -> OUT_OF_STOCK
        receive(gasket, 20, MovementReason.INITIAL_STOCK, 25);
        sell(gasket, 20, 8);

        Product bolts = product("6578903", "Bolt Pack", 50, "6.25");    // ends at 500 -> IN_STOCK
        receive(bolts, 500, MovementReason.INITIAL_STOCK, 30);
        sell(bolts, 120, 12);
        receive(bolts, 200, MovementReason.SUPPLIER_DELIVERY, 6);
        sell(bolts, 80, 2);

        Product cable = product("6578456", "USB Cable", 20, "3.20");    // ends at 42 -> IN_STOCK
        receive(cable, 60, MovementReason.INITIAL_STOCK, 28);
        sell(cable, 15, 15);
        move(cable, MovementType.ADJUSTMENT, -5, MovementReason.DAMAGED, 9);
        move(cable, MovementType.ADJUSTMENT, 2, MovementReason.RECOUNT, 3);

        Product labels = product("6578103", "Shipping Labels", 100, "0.05"); // ends at 50 -> LOW_STOCK
        receive(labels, 300, MovementReason.INITIAL_STOCK, 20);
        sell(labels, 250, 4);

        Product legacy = product("6570486", "Legacy Adapter", 5, "8.00"); // discontinued, history kept
        receive(legacy, 10, MovementReason.INITIAL_STOCK, 40);
        sell(legacy, 10, 35);
        legacy.setActive(false);
        products.save(legacy);
    }

    private Product product(String sku, String name, int threshold, String cost) {
        Product p = new Product(sku, name, threshold);
        p.setUnitCost(new BigDecimal(cost));
        return products.save(p);
    }

    private void receive(Product p, int qty, MovementReason reason, int daysAgo) {
        move(p, MovementType.RECEIPT, qty, reason, daysAgo);
    }

    private void sell(Product p, int qty, int daysAgo) {
        move(p, MovementType.FULFILLMENT, -qty, MovementReason.SALE, daysAgo);
    }

    private void move(Product p, MovementType type, int delta, MovementReason reason, int daysAgo) {
        stock.recordMovement(new RecordMovementCommand(p.getId(), type, delta, reason, null,
                clock.instant().minus(daysAgo, ChronoUnit.DAYS), "system"));
    }
}

