package invoSolutions.inventorytracker.service;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// No sku field: the SKU is the business key other systems refer to, so it never changes after creation.
public record UpdateProductCommand(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,
        @NotNull @Min(0) Integer reorderThreshold,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
        @NotNull Boolean active) { }
