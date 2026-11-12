package invoSolutions.inventorytracker.controller;

import invoSolutions.inventorytracker.exceptions.InsufficientStockException;
import invoSolutions.inventorytracker.exceptions.InvalidMovementException;
import invoSolutions.inventorytracker.model.*;
import invoSolutions.inventorytracker.service.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.*;
import java.util.List;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    private final ProductService products;
    private final StockService stock;

    public PageController(ProductService products, StockService stock) {
        this.products = products;
        this.stock = stock;
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String q,
                           @RequestParam(required = false) StockStatus status,
                           Model model) {
        model.addAttribute("products", products.list(q, status));
        model.addAttribute("q", q);     // echoed back so the search box keeps its text
        model.addAttribute("status", status);
        model.addAttribute("statuses", StockStatus.values()); // feeds the dropdown
        return "products";
    }

    /* ---- Movements page ----*/
    private static final int PAGE_SIZE = 20;

    @GetMapping("/movements")
    public String movements(@RequestParam(required = false) String q,
                            @RequestParam(required = false) Long productId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) MovementType type,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        model.addAttribute("types", MovementType.values());
        model.addAttribute("reasons", MovementReason.values());
        model.addAttribute("zone", ZoneId.systemDefault());

        // Nothing is looked up until the user searches: either by a picked product or by text.
        ProductView selected = null;
        if (productId != null) {
            selected = products.get(productId);
        } else if (q != null && !q.isBlank()) {
            List<ProductView> matches = products.list(q, null);
            if (matches.size() == 1) selected = matches.get(0);
            else model.addAttribute("matches", matches); // none -> message, several -> pick list
        }

        // After a product is chosen, the search box shows its SKU (unique), so re-running Lookup finds the same one.
        model.addAttribute("q", selected != null ? selected.product().getSku() : q);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("type", type);

        if (selected != null) {
            ZoneId zone = ZoneId.systemDefault();
            Instant start = (from == null) ? null : from.atStartOfDay(zone).toInstant();
            // "To" names a whole day, so the (exclusive) limit is the start of the next day.
            Instant end = (to == null) ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
            model.addAttribute("product", selected);
            model.addAttribute("history", stock.history(selected.product().getId(), start, end, type,
                    PageRequest.of(page, PAGE_SIZE)));
        }
        return "movements";
    }

    @PostMapping("/movements")
    public String record(@RequestParam Long productId,
                         @RequestParam MovementType type,
                         @RequestParam int quantity,
                         @RequestParam MovementReason reason,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAt,
                         RedirectAttributes flash) {
        // The form takes a plain number: the type decides the direction, except adjustments, which are typed signed.
        int delta = switch (type) {
            case RECEIPT -> Math.abs(quantity);
            case FULFILLMENT -> -Math.abs(quantity);
            case ADJUSTMENT -> quantity;
        };
        String cleanNote = (note == null || note.isBlank()) ? null : note.trim();
        Instant when = (occurredAt == null) ? null : occurredAt.atZone(ZoneId.systemDefault()).toInstant();

        try {
            StockMovement saved = stock.recordMovement(
                    new RecordMovementCommand(productId, type, delta, reason, cleanNote, when, null));
            flash.addFlashAttribute("success", "Recorded " + saved.getReference());
        } catch (InsufficientStockException | InvalidMovementException e) {
            // The service's own message is already user-readable, so show it as-is.
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/movements?productId=" + productId; // redirect so a refresh never re-posts the form
    }
}
