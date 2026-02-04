package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.example.demo.api.inventory.order.StockOrderDTO;
import com.example.demo.api.inventory.order.StockOrderService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductService productService;
    private final StockOrderService stockOrderService;

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
        model.addAttribute("totalProductCount", pageData.get("totalProductCount"));
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> initializeMonth(@RequestParam String yearMonth) {
        inventoryService.initializeMonthlyInventory(yearMonth);
        // 재고 현황에서 선택할 월 계산 (생성된 월 + 1)
        java.time.YearMonth ym = java.time.YearMonth.parse(yearMonth);
        String displayMonth = ym.plusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        return ResponseEntity.ok(Map.of("message", yearMonth + " 재고가 생성되었습니다.\n재고 현황에서 " + displayMonth + "을 선택하세요."));
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

    /**
     * FIFO 방식으로 재고 차감
     */
    @PostMapping("/api/inventory/stocks/{id}/deduct")
    @ResponseBody
    public ResponseEntity<InventoryDTO> deductStock(@PathVariable Long id,
                                                     @RequestParam BigDecimal quantity) {
        return ResponseEntity.ok(inventoryService.deductStockWithFIFO(id, quantity));
    }

    /**
     * 재고 복구 (역FIFO - 실수로 차감한 경우)
     */
    @PostMapping("/api/inventory/stocks/{id}/restore")
    @ResponseBody
    public ResponseEntity<InventoryDTO> restoreStock(@PathVariable Long id,
                                                      @RequestParam BigDecimal quantity) {
        return ResponseEntity.ok(inventoryService.restoreStock(id, quantity));
    }

    /**
     * 유효기간 소진완료 처리
     */
    @PostMapping("/api/inventory/orders/{orderId}/consume")
    @ResponseBody
    public ResponseEntity<StockOrderDTO> markAsConsumed(@PathVariable Long orderId) {
        return ResponseEntity.ok(stockOrderService.markAsConsumed(orderId));
    }

    /**
     * 유효기간 소진완료 취소
     */
    @PostMapping("/api/inventory/orders/{orderId}/unconsume")
    @ResponseBody
    public ResponseEntity<StockOrderDTO> unmarkAsConsumed(@PathVariable Long orderId) {
        return ResponseEntity.ok(stockOrderService.unmarkAsConsumed(orderId));
    }

    /**
     * 유효기간 수량 설정 (수량미상 데이터에 수량 입력)
     */
    @PostMapping("/api/inventory/orders/{orderId}/set-quantity")
    @ResponseBody
    public ResponseEntity<StockOrderDTO> setQuantity(@PathVariable Long orderId,
                                                      @RequestParam BigDecimal quantity) {
        return ResponseEntity.ok(stockOrderService.setQuantity(orderId, quantity));
    }

    /**
     * 제품의 활성 유효기간 목록 조회
     */
    @GetMapping("/api/inventory/stocks/product/{productId}/expiry")
    @ResponseBody
    public ResponseEntity<List<StockOrderDTO>> getActiveExpiryDates(@PathVariable Long productId) {
        return ResponseEntity.ok(stockOrderService.getActiveExpiryDatesByProduct(productId));
    }

    /**
     * 제품별 + 년월별 사용량 변경 이력 조회
     */
    @GetMapping("/api/inventory/stocks/product/{productId}/usage-logs")
    @ResponseBody
    public ResponseEntity<List<UsageLogDTO>> getUsageLogs(@PathVariable Long productId,
                                                           @RequestParam String yearMonth) {
        return ResponseEntity.ok(inventoryService.getUsageLogsByProductIdAndYearMonth(productId, yearMonth));
    }
}
