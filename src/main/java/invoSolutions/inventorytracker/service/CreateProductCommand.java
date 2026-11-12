package invoSolutions.inventorytracker.service;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductCommand(
        @NotBlank @Pattern(regexp = "\\d+", message = "SKU must contain numbers only") @Size(max = 12) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description,

        // Integer + @NotNull so a missing field is an error, not a silent 0.
        @NotNull @Min(0) Integer reorderThreshold,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) { }
