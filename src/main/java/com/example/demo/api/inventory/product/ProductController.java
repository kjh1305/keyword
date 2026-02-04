package com.example.demo.api.inventory.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // View Controllers
    @GetMapping("/inventory/products")
    public String productList(Model model,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        Page<ProductDTO> products = productService.getProducts(
                category, keyword,
                PageRequest.of(page, size, Sort.by("name").ascending())
        );
        model.addAttribute("products", products);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("keyword", keyword);
        model.addAttribute("menu", "products");
        return "inventory/product-list";
    }

    // REST API Controllers
    @GetMapping("/api/inventory/products")
    @ResponseBody
    public ResponseEntity<Page<ProductDTO>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ProductDTO> products = productService.getProducts(
                category, keyword,
                PageRequest.of(page, size, Sort.by("name").ascending())
        );
        return ResponseEntity.ok(products);
    }

    @GetMapping("/api/inventory/products/all")
    @ResponseBody
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/api/inventory/products/{id}")
    @ResponseBody
    public ResponseEntity<ProductDTO> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/api/inventory/products/categories")
    @ResponseBody
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(productService.getAllCategories());
    }

    @PostMapping("/api/inventory/products")
    @ResponseBody
    public ResponseEntity<ProductDTO> createProduct(@RequestBody ProductDTO dto) {
        return ResponseEntity.ok(productService.createProduct(dto));
    }

    @PutMapping("/api/inventory/products/{id}")
    @ResponseBody
    public ResponseEntity<ProductDTO> updateProduct(@PathVariable Long id, @RequestBody ProductDTO dto) {
        return ResponseEntity.ok(productService.updateProduct(id, dto));
    }

    @DeleteMapping("/api/inventory/products/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/api/inventory/products/{id}/add-to-inventory", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> addProductToInventory(@PathVariable Long id) {
        try {
            String message = productService.addProductToInventory(id);
            return ResponseEntity.ok(message);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
