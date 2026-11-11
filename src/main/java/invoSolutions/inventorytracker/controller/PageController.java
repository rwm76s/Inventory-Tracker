package invoSolutions.inventorytracker.controller;

import invoSolutions.inventorytracker.model.StockStatus;
import invoSolutions.inventorytracker.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    private final ProductService products;

    public PageController(ProductService products) {
        this.products = products;
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
}
