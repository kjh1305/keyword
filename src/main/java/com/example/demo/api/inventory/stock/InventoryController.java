package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductService productService;

    // View Controllers
    @GetMapping("/inventory/stocks")
    public String stockList(Model model,
                            @RequestParam(required = false) String yearMonth,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String stockFilter,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            yearMonth = inventoryService.getCurrentYearMonth();
        }

        java.util.Map<String, Object> pageData = inventoryService.getInventoryByMonthPaged(yearMonth, category, keyword, stockFilter, page, size);
        List<String> yearMonths = inventoryService.getAllYearMonths();

        if (!yearMonths.contains(yearMonth)) {
            yearMonths.add(0, yearMonth);
        }

        model.addAttribute("inventories", pageData.get("content"));
        model.addAttribute("currentPage", pageData.get("currentPage"));
        model.addAttribute("totalPages", pageData.get("totalPages"));
        model.addAttribute("totalElements", pageData.get("totalElements"));
        model.addAttribute("hasNext", pageData.get("hasNext"));
        model.addAttribute("hasPrevious", pageData.get("hasPrevious"));
        model.addAttribute("outOfStockCount", pageData.get("outOfStockCount"));
        model.addAttribute("lowStockCount", pageData.get("lowStockCount"));
        model.addAttribute("expiryWarningCount", pageData.get("expiryWarningCount"));
        model.addAttribute("yearMonths", yearMonths);
        model.addAttribute("selectedYearMonth", yearMonth);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("keyword", keyword);
        model.addAttribute("stockFilter", stockFilter);
        model.addAttribute("size", size);
        model.addAttribute("menu", "stocks");

        return "inventory/inventory-list";
    }

    // REST API Controllers
    @GetMapping("/api/inventory/stocks")
    @ResponseBody
    public ResponseEntity<List<InventoryDTO>> getInventory(
            @RequestParam String yearMonth,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(inventoryService.getInventoryByMonth(yearMonth, category, keyword));
    }

    @GetMapping("/api/inventory/stocks/{id}")
    @ResponseBody
    public ResponseEntity<InventoryDTO> getInventoryById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getInventoryById(id));
    }

    @PutMapping("/api/inventory/stocks/{id}")
    @ResponseBody
    public ResponseEntity<InventoryDTO> updateInventory(@PathVariable Long id, @RequestBody InventoryDTO dto) {
        return ResponseEntity.ok(inventoryService.updateInventory(id, dto));
    }

    @PostMapping("/api/inventory/stocks")
    @ResponseBody
    public ResponseEntity<InventoryDTO> createInventory(@RequestBody InventoryDTO dto) {
        return ResponseEntity.ok(inventoryService.createOrUpdateInventory(dto.getProductId(), dto.getYearMonth(), dto));
    }

    @DeleteMapping("/api/inventory/stocks/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteInventory(@PathVariable Long id) {
        inventoryService.deleteInventory(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/inventory/stocks/initialize")
    @ResponseBody
    public ResponseEntity<Map<String, String>> initializeMonth(@RequestParam String yearMonth) {
        inventoryService.initializeMonthlyInventory(yearMonth);
        return ResponseEntity.ok(Map.of("message", yearMonth + " 재고가 생성되었습니다."));
    }

    @GetMapping("/api/inventory/stocks/months")
    @ResponseBody
    public ResponseEntity<List<String>> getYearMonths() {
        return ResponseEntity.ok(inventoryService.getAllYearMonths());
    }

    @GetMapping("/api/inventory/stocks/product/{productId}/history")
    @ResponseBody
    public ResponseEntity<List<InventoryDTO>> getProductHistory(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getProductHistory(productId));
    }
}
