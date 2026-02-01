package com.example.demo.api.inventory.order;

import com.example.demo.api.inventory.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class StockOrderController {

    private final StockOrderService stockOrderService;
    private final ProductService productService;

    // View Controllers
    @GetMapping("/inventory/orders")
    public String orderList(Model model,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        LocalDate start = (startDate != null && !startDate.isEmpty()) ? LocalDate.parse(startDate) : null;
        LocalDate end = (endDate != null && !endDate.isEmpty()) ? LocalDate.parse(endDate) : null;

        Page<StockOrderDTO> orders = stockOrderService.searchOrdersPaged(category, keyword, status, start, end, page, size);

        model.addAttribute("orders", orders);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("products", productService.getAllProducts());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("menu", "orders");

        return "inventory/order-list";
    }

    // REST API Controllers
    @GetMapping("/api/inventory/orders")
    @ResponseBody
    public ResponseEntity<List<StockOrderDTO>> getOrders(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(stockOrderService.searchOrders(category, keyword, status));
    }

    @GetMapping("/api/inventory/orders/pending")
    @ResponseBody
    public ResponseEntity<List<StockOrderDTO>> getPendingOrders() {
        return ResponseEntity.ok(stockOrderService.getPendingOrders());
    }

    @GetMapping("/api/inventory/orders/completed")
    @ResponseBody
    public ResponseEntity<List<StockOrderDTO>> getCompletedOrders() {
        return ResponseEntity.ok(stockOrderService.getCompletedOrders());
    }

    @GetMapping("/api/inventory/orders/{id}")
    @ResponseBody
    public ResponseEntity<StockOrderDTO> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(stockOrderService.getOrderById(id));
    }

    @PostMapping("/api/inventory/orders")
    @ResponseBody
    public ResponseEntity<StockOrderDTO> createOrder(@RequestBody StockOrderDTO dto) {
        return ResponseEntity.ok(stockOrderService.createOrder(dto));
    }

    @PostMapping("/api/inventory/orders/{id}/complete")
    @ResponseBody
    public ResponseEntity<StockOrderDTO> completeOrder(
            @PathVariable Long id,
            @RequestParam(required = false) java.math.BigDecimal quantity,
            @RequestParam(required = false) java.time.LocalDate expiryDate) {
        return ResponseEntity.ok(stockOrderService.completeOrder(id, quantity, expiryDate));
    }

    @PutMapping("/api/inventory/orders/{id}")
    @ResponseBody
    public ResponseEntity<StockOrderDTO> updateOrder(@PathVariable Long id, @RequestBody StockOrderDTO dto) {
        return ResponseEntity.ok(stockOrderService.updateOrder(id, dto));
    }

    @DeleteMapping("/api/inventory/orders/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        stockOrderService.deleteOrder(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/inventory/orders/product/{productId}")
    @ResponseBody
    public ResponseEntity<List<StockOrderDTO>> getOrdersByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(stockOrderService.getOrdersByProduct(productId));
    }

    @GetMapping("/api/inventory/orders/product/{productId}/expiry")
    @ResponseBody
    public ResponseEntity<List<StockOrderDTO>> getExpiryHistoryByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(stockOrderService.getExpiryHistoryByProduct(productId));
    }

    @GetMapping("/api/inventory/orders/product/{productId}/expiry/paged")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getExpiryHistoryByProductPaged(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(stockOrderService.getExpiryHistoryByProductPaged(productId, page, size));
    }
}
