package invoSolutions.inventorytracker.api;

import invoSolutions.inventorytracker.model.StockMovement;
import invoSolutions.inventorytracker.model.StockStatus;
import invoSolutions.inventorytracker.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService products;
    private final StockService stock;

    public ProductApiController(ProductService products, StockService stock) {
        this.products = products;
        this.stock = stock;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) StockStatus status) {
        return products.list(q, status).stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(products.get(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductCommand cmd) {
        ProductResponse created = ProductResponse.from(products.create(cmd));
        // 201 + Location is the REST convention for "created, and here is where it lives".
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductCommand cmd) {
        return ProductResponse.from(products.update(id, cmd));
    }

    @PostMapping("/{id}/movements")
    public ResponseEntity<MovementResponse> recordMovement(@PathVariable Long id,
                                                           @Valid @RequestBody MovementRequest req) {
        StockMovement saved = stock.recordMovement(new RecordMovementCommand(
                id, req.type(), req.quantityDelta(), req.reason(), req.note(), req.occurredAt(), req.createdBy()));
        return ResponseEntity.status(HttpStatus.CREATED).body(MovementResponse.from(saved));
    }

    @GetMapping("/{id}/movements")
    public PagedModel<MovementResponse> history(@PathVariable Long id,
                                                @PageableDefault(size = 20) Pageable pageable) {
        // PagedModel gives stable JSON ({content, page}); returning Spring's Page directly is discouraged.
        return new PagedModel<>(stock.history(id, null, null, null, pageable).map(MovementResponse::from));
    }
}
