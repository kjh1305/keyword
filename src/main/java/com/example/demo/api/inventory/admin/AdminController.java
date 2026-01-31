package com.example.demo.api.inventory.admin;

import com.example.demo.api.inventory.log.ActivityLogService;
import com.example.demo.api.inventory.order.StockOrderRepository;
import com.example.demo.api.inventory.product.ProductRepository;
import com.example.demo.api.inventory.stock.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockOrderRepository stockOrderRepository;
    private final ActivityLogService activityLogService;

    // View - 관리자 페이지
    @GetMapping("/inventory/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminPage(Model model) {
        model.addAttribute("productCount", productRepository.count());
        model.addAttribute("inventoryCount", inventoryRepository.count());
        model.addAttribute("orderCount", stockOrderRepository.count());
        model.addAttribute("menu", "admin");
        return "inventory/admin";
    }

    // API - 재고 데이터 전체 삭제
    @DeleteMapping("/api/inventory/admin/stocks")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAllStocks() {
        long count = inventoryRepository.count();
        inventoryRepository.deleteAll();

        activityLogService.log("DELETE", "SYSTEM", null, "재고 전체 삭제", count + "건 삭제");

        Map<String, Object> result = new HashMap<>();
        result.put("message", "재고 데이터 " + count + "건이 삭제되었습니다.");
        result.put("deletedCount", count);
        return ResponseEntity.ok(result);
    }

    // API - 상품 데이터 전체 삭제 (재고, 주문도 함께 삭제)
    @DeleteMapping("/api/inventory/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAllProducts() {
        long orderCount = stockOrderRepository.count();
        long inventoryCount = inventoryRepository.count();
        long productCount = productRepository.count();

        // 순서대로 삭제 (외래키 제약)
        stockOrderRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        activityLogService.log("DELETE", "SYSTEM", null, "전체 데이터 삭제",
                "상품: " + productCount + ", 재고: " + inventoryCount + ", 주문: " + orderCount);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "상품 " + productCount + "건, 재고 " + inventoryCount + "건, 주문 " + orderCount + "건이 삭제되었습니다.");
        result.put("deletedProducts", productCount);
        result.put("deletedInventories", inventoryCount);
        result.put("deletedOrders", orderCount);
        return ResponseEntity.ok(result);
    }

    // API - 주문/입고 데이터 전체 삭제
    @DeleteMapping("/api/inventory/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAllOrders() {
        long count = stockOrderRepository.count();
        stockOrderRepository.deleteAll();

        activityLogService.log("DELETE", "SYSTEM", null, "주문/입고 전체 삭제", count + "건 삭제");

        Map<String, Object> result = new HashMap<>();
        result.put("message", "주문/입고 데이터 " + count + "건이 삭제되었습니다.");
        result.put("deletedCount", count);
        return ResponseEntity.ok(result);
    }

    // API - 데이터 현황
    @GetMapping("/api/inventory/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("productCount", productRepository.count());
        stats.put("inventoryCount", inventoryRepository.count());
        stats.put("orderCount", stockOrderRepository.count());
        return ResponseEntity.ok(stats);
    }
}
