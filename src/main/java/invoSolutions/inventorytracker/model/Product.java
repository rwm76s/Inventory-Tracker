package invoSolutions.inventorytracker.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A stockable item. Note there is deliberately NO quantity field: on-hand is
 * derived from the stock_movement ledger so there is a single source of truth.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Business key shown to users; unique index also gives fast lookup by SKU.
    @NotBlank
    @Size(max = 64)
    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    // At or below this on-hand quantity the product is set as LOW_STOCK.
    @Min(0)
    @Column(nullable = false)
    private int reorderThreshold;

    // BigDecimal for handling money
    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    // Allows users to know if the product is discontinued.
    @Column(nullable = false)
    private boolean active = true;

    // Timestamp for all stock movement
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() { } // required by JPA

    public Product(String sku, String name, String unit, int reorderPoint) {
        this.sku = sku;
        this.name = name;
        this.reorderThreshold = reorderPoint;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public String getSku() { return sku; }

    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public int getReorderThreshold() { return reorderThreshold; }

    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold = reorderThreshold; }

    public BigDecimal getUnitCost() { return unitCost; }

    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
}