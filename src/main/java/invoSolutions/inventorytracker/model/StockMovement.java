package invoSolutions.inventorytracker.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * One append-only ledger entry. Mistakes are corrected with a new ADJUSTMENT,
 * never by editing or deleting a row, so history is always trustworthy.
 */
@Entity
@Immutable // Ignores updates
@Table(name = "stock_movement",
        // Handles both "history for a product" and "on-hand as of a date" queries.
        indexes = @Index(name = "idx_movement_product_occurred", columnList = "product_id, occurred_at")
)
// The database itself rejects a sign that contradicts the type.
@Check(constraints = "quantity_delta <> 0 AND ("
        + "(movement_type = 'RECEIPT' AND quantity_delta > 0) OR "
        + "(movement_type = 'ISSUE' AND quantity_delta < 0) OR "
        + "movement_type = 'ADJUSTMENT')")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lazy fetch so listing movements doesn't drag in a full Product row each time.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    // STRING, not ORDINAL: reordering the enum later must not corrupt old rows.
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private MovementType type;

    // positive = in, negative = out. One column makes on-hand a simple SUM.
    @Column(nullable = false, updatable = false)
    private int quantityDelta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private MovementReason reason;

    @Column(length = 500, updatable = false)
    private String note;

    // When the stock actually moved...
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    // ...versus when someone recorded it. Keeping both makes backdating auditable.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false, length = 100)
    private String createdBy;

    protected StockMovement() { } // required by JPA

    public StockMovement(Product product, MovementType type, int quantityDelta,
                         MovementReason reason, String note, Instant occurredAt, String createdBy) {
        if (!type.acceptsDelta(quantityDelta)) {
            throw new IllegalArgumentException(
                    type + " movement cannot have quantity delta " + quantityDelta);
        }
        this.product = product;
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
        this.note = note;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.createdBy = createdBy != null ? createdBy : "system";
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /**
     * Human-friendly "transaction number", derived from the primary key so it is
     * unique by construction and needs no extra column or sequence. (Not persisted.)
     */
    public String getReference() {
        return id == null ? null : String.format("MOV-%06d", id);
    }

    public Long getId() { return id; }

    public Product getProduct() { return product; }

    public MovementType getType() { return type; }

    public int getQuantityDelta() { return quantityDelta; }

    public MovementReason getReason() { return reason; }

    public String getNote() { return note; }

    public Instant getOccurredAt() { return occurredAt; }

    public Instant getCreatedAt() { return createdAt; }

    public String getCreatedBy() { return createdBy; }

}