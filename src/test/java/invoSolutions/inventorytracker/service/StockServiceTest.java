package invoSolutions.inventorytracker.service;

import invoSolutions.inventorytracker.exceptions.*;
import invoSolutions.inventorytracker.model.*;
import invoSolutions.inventorytracker.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional // each test rolls back, so tests never see each other's ledger rows
class StockServiceTest {

    // "Now" is pinned so backdating tests don't depend on when they run.
    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired
    StockService service;

    @Autowired
    ProductRepository products;

    private Product product;

    @BeforeEach
    void createProduct() {
        product = products.save(new Product("SKU-1", "Widget", 5)); // reorder threshold = 5
    }

    // ---- helpers: tests read as a timeline instead of constructor noise ----

    private static Instant daysAgo(int days) { return NOW.minus(days, ChronoUnit.DAYS); }

    private StockMovement receive(int qty, Instant at) {
        return service.recordMovement(new RecordMovementCommand(product.getId(),
                MovementType.RECEIPT, qty, MovementReason.SUPPLIER_DELIVERY, null, at, "test"));
    }

    private StockMovement fulfill(int qty, Instant at) {
        return service.recordMovement(new RecordMovementCommand(product.getId(),
                MovementType.FULFILLMENT, -qty, MovementReason.SALE, null, at, "test"));
    }

    /** Day 1: +10, Day 2: -8, Day 3: +20 -> 2 after day 2, 22 now. */
    private void buildDipTimeline() {
        receive(10, daysAgo(3));
        fulfill(8, daysAgo(2));
        receive(20, daysAgo(1));
    }

    // ---- the headline rule: a backdated removal must not break any later balance ----

    @Test
    void backdatedFulfillmentIsRejectedWhenItWouldCauseANegativeBalanceBetweenThenAndNow() {
        buildDipTimeline();

        // Fine at day 1 (10-5) and fine today (22-5), but day 2 would be 2-5 = -3.
        assertThatThrownBy(() -> fulfill(5, daysAgo(3).plusSeconds(60)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("-3");

        assertThat(service.onHand(product.getId())).isEqualTo(22); // nothing was written
    }

    @Test
    void backdatedFulfillmentIsAcceptedWhenEveryLaterBalanceStaysNonNegative() {
        buildDipTimeline();

        // Day 2 drops to exactly 0, which is allowed: the rule is "never negative", not "never zero".
        fulfill(2, daysAgo(3).plusSeconds(60));

        assertThat(service.onHand(product.getId())).isEqualTo(20);
    }

    @Test
    void backdatingBeforeAnyStockExistedIsRejected() {
        receive(10, daysAgo(3));

        assertThatThrownBy(() -> fulfill(1, daysAgo(5)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Only 0 on hand");
    }

    // ---- boundaries of the simple (current stock) case ----

    @Test
    void fulfillingMoreThanOnHandIsRejectedAndLeavesTheLedgerUntouched() {
        receive(5, daysAgo(1));

        assertThatThrownBy(() -> fulfill(6, null)).isInstanceOf(InsufficientStockException.class);

        assertThat(service.onHand(product.getId())).isEqualTo(5);
    }

    @Test
    void fulfillingExactlyWhatIsOnHandIsAllowedAndReportsOutOfStock() {
        receive(5, daysAgo(1));

        fulfill(5, null);

        assertThat(service.onHand(product.getId())).isZero();
        assertThat(service.statusOf(product.getId())).isEqualTo(StockStatus.OUT_OF_STOCK);
    }

    @Test
    void movementAtTheSameInstantCountsAsAlreadyHappened() {
        // Ledger order within one instant is by id, so the later-recorded movement goes after.
        Instant t = daysAgo(1);
        receive(5, t);

        fulfill(5, t);

        assertThat(service.onHand(product.getId())).isZero();
    }

    // ---- input validation ----

    @Test
    void futureDatedMovementIsRejected() {
        assertThatThrownBy(() -> receive(5, NOW.plusSeconds(1)))
                .isInstanceOf(InvalidMovementException.class);
    }

    @Test
    void movementWithNoDateDefaultsToNow() {
        StockMovement m = receive(5, null);

        assertThat(m.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void signThatContradictsTheTypeIsRejectedAsAnInvalidMovement() {
        // Must surface our exception, not the entity's raw IllegalArgumentException.
        assertThatThrownBy(() -> service.recordMovement(new RecordMovementCommand(
                product.getId(), MovementType.RECEIPT, -3, MovementReason.SUPPLIER_DELIVERY, null, null, "test")))
                .isInstanceOf(InvalidMovementException.class);
    }

    @Test
    void reasonOtherRequiresANote() {
        assertThatThrownBy(() -> service.recordMovement(new RecordMovementCommand(
                product.getId(), MovementType.ADJUSTMENT, 1, MovementReason.OTHER, "  ", null, "test")))
                .isInstanceOf(InvalidMovementException.class);

        // ...and the same movement with a note goes through.
        service.recordMovement(new RecordMovementCommand(
                product.getId(), MovementType.ADJUSTMENT, 1, MovementReason.OTHER, "found behind shelf", null, "test"));
        assertThat(service.onHand(product.getId())).isEqualTo(1);
    }

    @Test
    void unknownProductIsRejected() {
        assertThatThrownBy(() -> service.recordMovement(new RecordMovementCommand(
                999_999L, MovementType.RECEIPT, 1, MovementReason.SUPPLIER_DELIVERY, null, null, "test")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // ---- derived status ----

    @Test
    void statusIsDerivedFromOnHandAndTheReorderThreshold() {
        receive(6, daysAgo(2));
        assertThat(service.statusOf(product.getId())).isEqualTo(StockStatus.IN_STOCK);

        fulfill(1, daysAgo(1)); // 5 == threshold -> low
        assertThat(service.statusOf(product.getId())).isEqualTo(StockStatus.LOW_STOCK);

        fulfill(5, null);
        assertThat(service.statusOf(product.getId())).isEqualTo(StockStatus.OUT_OF_STOCK);
    }
}
