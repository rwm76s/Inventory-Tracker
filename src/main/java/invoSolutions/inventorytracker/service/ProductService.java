package invoSolutions.inventorytracker.service;

import invoSolutions.inventorytracker.exceptions.DuplicateSkuException;
import invoSolutions.inventorytracker.exceptions.ProductNotFoundException;
import invoSolutions.inventorytracker.model.Product;
import invoSolutions.inventorytracker.model.StockStatus;
import invoSolutions.inventorytracker.repository.ProductRepository;
import invoSolutions.inventorytracker.repository.StockMovementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository products;
    private final StockMovementRepository movements;

    public ProductService(ProductRepository products, StockMovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<ProductView> list(String q, StockStatus status) {
        Sort byName = Sort.by("name").ascending();
        List<Product> found = (q == null || q.isBlank())
                ? products.findAll(byName)
                : products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(q.trim(), q.trim(), byName);

        Map<Long, Long> onHand = onHandFor(found);
        return found.stream()
                .map(p -> new ProductView(p, onHand.getOrDefault(p.getId(), 0L)))
                // Status is derived, so it's filtered here rather than in SQL.
                .filter(v -> status == null || v.status() == status)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductView get(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return new ProductView(p, movements.onHand(id));
    }

    @Transactional
    public ProductView create(CreateProductCommand cmd) {
        String sku = cmd.sku().trim();
        if (products.existsBySku(sku)) throw new DuplicateSkuException(sku); // friendly path
        Product p = new Product(sku, cmd.name().trim(), cmd.reorderThreshold());
        p.setDescription(cmd.description());
        p.setUnitCost(cmd.unitCost());
        try {
            p = products.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous creates can both pass the check above; the unique index is the real guard.
            throw new DuplicateSkuException(sku);
        }
        return new ProductView(p, 0); // a new product starts at zero; stock arrives via a movement
    }

    @Transactional
    public ProductView update(Long id, UpdateProductCommand cmd) {
        Product p = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        p.setName(cmd.name().trim());
        p.setDescription(cmd.description());
        p.setReorderThreshold(cmd.reorderThreshold());
        p.setUnitCost(cmd.unitCost());
        p.setActive(cmd.active());
        return new ProductView(p, movements.onHand(id)); // saved by dirty checking at commit
    }

    private Map<Long, Long> onHandFor(List<Product> found) {
        if (found.isEmpty()) return Map.of(); // an empty IN () list is invalid SQL on some databases
        return movements.onHandForProducts(found.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(StockMovementRepository.OnHandRow::getProductId, StockMovementRepository.OnHandRow::getOnHand));
    }
}
