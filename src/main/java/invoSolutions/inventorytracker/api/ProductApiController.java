package invoSolutions.inventorytracker.api;

import invoSolutions.inventorytracker.model.StockStatus;
import invoSolutions.inventorytracker.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService products;

    public ProductApiController(ProductService products) { this.products = products; }

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
}
