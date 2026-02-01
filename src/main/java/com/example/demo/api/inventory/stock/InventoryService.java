package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.log.ActivityLogService;
import com.example.demo.api.inventory.order.StockOrder;
import com.example.demo.api.inventory.order.StockOrderRepository;
import com.example.demo.api.inventory.product.Product;
import com.example.demo.api.inventory.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ActivityLogService activityLogService;
    private final StockOrderRepository stockOrderRepository;

    public List<InventoryDTO> getInventoryByMonth(String yearMonth, String category, String keyword) {
        String cat = (category == null || category.isEmpty()) ? null : category;
        String kw = (keyword == null || keyword.isEmpty()) ? null : keyword;

        List<Inventory> inventories = inventoryRepository.searchInventory(yearMonth, cat, kw);
        return inventories.stream()
                .map(InventoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getInventoryByMonthPaged(String yearMonth, String category, String keyword, String stockFilter, int page, int size) {
        String cat = (category == null || category.isEmpty()) ? null : category;
        String kw = (keyword == null || keyword.isEmpty()) ? null : keyword;
        String filter = (stockFilter == null || stockFilter.isEmpty()) ? null : stockFilter;

        Pageable pageable = PageRequest.of(page, size);
        Page<Inventory> inventoryPage = inventoryRepository.searchInventoryPaged(yearMonth, cat, kw, pageable);

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysFromNow = today.plusDays(30);

        List<InventoryDTO> content = inventoryPage.getContent().stream()
                .map(inv -> {
                    InventoryDTO dto = InventoryDTO.fromEntity(inv);
                    // StockOrder에서 해당 제품의 유효기간 목록 가져오기 (유효기간 빠른 순)
                    List<StockOrder> orders = stockOrderRepository.findAllExpiryByProductId(inv.getProduct().getId());

                    // 유효기간 문자열 목록 (미소진 + 유효기간 지나지 않은 것만)
                    List<String> expiryDates = orders.stream()
                            .filter(o -> o.getExpiryDate() != null
                                    && !o.getExpiryDate().isBefore(today)
                                    && (o.getConsumed() == null || !o.getConsumed()))
                            .map(o -> o.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")))
                            .distinct()
                            .collect(Collectors.toList());
                    dto.setExpiryDates(expiryDates);

                    // 유효기간별 상세정보 목록 (소진된 것 포함, UI에서 표시용)
                    List<InventoryDTO.ExpiryInfo> expiryInfoList = orders.stream()
                            .map(o -> InventoryDTO.ExpiryInfo.builder()
                                    .orderId(o.getId())
                                    .expiryDate(o.getExpiryDate())
                                    .expiryDateStr(o.getExpiryDate() != null ?
                                            o.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")) : null)
                                    .quantity(o.getQuantity())
                                    .remainingQuantity(o.getRemainingQuantity())
                                    .consumed(o.getConsumed() != null ? o.getConsumed() : false)
                                    .hasQuantity(o.getRemainingQuantity() != null)
                                    .build())
                            .collect(Collectors.toList());
                    dto.setExpiryInfoList(expiryInfoList);

                    // 유효기간 임박 여부 (30일 이내, 미소진)
                    boolean hasExpiryWarning = orders.stream()
                            .anyMatch(o -> o.getExpiryDate() != null
                                    && !o.getExpiryDate().isBefore(today)
                                    && !o.getExpiryDate().isAfter(thirtyDaysFromNow)
                                    && (o.getConsumed() == null || !o.getConsumed()));
                    dto.setExpiryWarning(hasExpiryWarning);

                    return dto;
                })
                .collect(Collectors.toList());

        // stockFilter 적용
        if (filter != null) {
            content = content.stream()
                    .filter(dto -> {
                        switch (filter) {
                            case "outOfStock":
                                return dto.getRemainingStock() != null && dto.getRemainingStock().compareTo(BigDecimal.ZERO) <= 0;
                            case "lowStock":
                                BigDecimal remaining = dto.getRemainingStock() != null ? dto.getRemainingStock() : BigDecimal.ZERO;
                                BigDecimal minQty = dto.getMinQuantity() != null ? new BigDecimal(dto.getMinQuantity()) : BigDecimal.ZERO;
                                return remaining.compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(minQty) <= 0;
                            case "expiryWarning":
                                return dto.isExpiryWarning();
                            default:
                                return true;
                        }
                    })
                    .collect(Collectors.toList());
        }

        // 전체 재고 부족/없음 수량 계산 (별도 쿼리)
        List<Inventory> allInventories = inventoryRepository.searchInventory(yearMonth, cat, kw);

        // 재고 없음 (0 이하)
        long outOfStockCount = allInventories.stream()
                .filter(inv -> {
                    BigDecimal remaining = inv.getRemainingStock() != null ? inv.getRemainingStock() : BigDecimal.ZERO;
                    return remaining.compareTo(BigDecimal.ZERO) <= 0;
                })
                .count();

        // 재고 부족 (최소수량 이하, 0 초과)
        long lowStockCount = allInventories.stream()
                .filter(inv -> {
                    BigDecimal remaining = inv.getRemainingStock() != null ? inv.getRemainingStock() : BigDecimal.ZERO;
                    BigDecimal minQty = inv.getProduct().getMinQuantity() != null
                            ? new BigDecimal(inv.getProduct().getMinQuantity())
                            : BigDecimal.ZERO;
                    return remaining.compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(minQty) <= 0;
                })
                .count();

        // 유효기간 임박 (30일 이내) - StockOrder에서 확인
        LocalDate thirtyDaysLater = LocalDate.now().plusDays(30);
        long expiryWarningCount = allInventories.stream()
                .filter(inv -> {
                    List<StockOrder> orders = stockOrderRepository.findCompletedWithExpiryByProductId(inv.getProduct().getId());
                    return orders.stream()
                            .anyMatch(o -> o.getExpiryDate() != null
                                    && !o.getExpiryDate().isBefore(LocalDate.now())
                                    && !o.getExpiryDate().isAfter(thirtyDaysLater));
                })
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("currentPage", inventoryPage.getNumber());
        result.put("totalPages", inventoryPage.getTotalPages());
        result.put("totalElements", inventoryPage.getTotalElements());
        result.put("hasNext", inventoryPage.hasNext());
        result.put("hasPrevious", inventoryPage.hasPrevious());
        result.put("outOfStockCount", outOfStockCount);
        result.put("lowStockCount", lowStockCount);
        result.put("expiryWarningCount", expiryWarningCount);

        return result;
    }

    public List<String> getAllYearMonths() {
        List<String> months = inventoryRepository.findAllYearMonths();
        if (months.isEmpty()) {
            months = new ArrayList<>();
            months.add(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        }
        return months;
    }

    public String getCurrentYearMonth() {
        return YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public InventoryDTO getInventoryById(Long id) {
        return inventoryRepository.findById(id)
                .map(InventoryDTO::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보를 찾을 수 없습니다: " + id));
    }

    @Transactional
    public InventoryDTO updateInventory(Long id, InventoryDTO dto) {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보를 찾을 수 없습니다: " + id));

        if (dto.getInitialStock() != null) {
            inventory.setInitialStock(dto.getInitialStock());
        }
        if (dto.getAddedStock() != null) {
            inventory.setAddedStock(dto.getAddedStock());
        }
        if (dto.getUsedQuantity() != null) {
            inventory.setUsedQuantity(dto.getUsedQuantity());
        }
        if (dto.getExpiryDate() != null) {
            inventory.setExpiryDate(dto.getExpiryDate());
        }
        if (dto.getNote() != null) {
            inventory.setNote(dto.getNote());
        }

        // 남은재고 명시적 계산: 월초재고 + 추가재고 - 사용량
        BigDecimal addedStock = inventory.getAddedStock() != null ? inventory.getAddedStock() : BigDecimal.ZERO;
        inventory.setRemainingStock(inventory.getInitialStock().add(addedStock).subtract(inventory.getUsedQuantity()));

        Inventory saved = inventoryRepository.saveAndFlush(inventory);
        activityLogService.logUpdate("INVENTORY", saved.getId(), saved.getProduct().getName(),
                "월: " + saved.getYearMonth());
        return InventoryDTO.fromEntity(saved);
    }

    @Transactional
    public InventoryDTO createOrUpdateInventory(Long productId, String yearMonth, InventoryDTO dto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다: " + productId));

        Inventory inventory = inventoryRepository.findByProductIdAndYearMonth(productId, yearMonth)
                .orElse(Inventory.builder()
                        .product(product)
                        .yearMonth(yearMonth)
                        .build());

        if (dto.getInitialStock() != null) {
            inventory.setInitialStock(dto.getInitialStock());
        }
        if (dto.getUsedQuantity() != null) {
            inventory.setUsedQuantity(dto.getUsedQuantity());
        }
        if (dto.getExpiryDate() != null) {
            inventory.setExpiryDate(dto.getExpiryDate());
        }

        return InventoryDTO.fromEntity(inventoryRepository.save(inventory));
    }

    @Transactional
    public void initializeMonthlyInventory(String yearMonth) {
        List<Product> allProducts = productRepository.findAll();

        for (Product product : allProducts) {
            if (!inventoryRepository.existsByProductIdAndYearMonth(product.getId(), yearMonth)) {
                Inventory previousInventory = getPreviousMonthInventory(product.getId(), yearMonth);

                Inventory.InventoryBuilder builder = Inventory.builder()
                        .product(product)
                        .yearMonth(yearMonth)
                        .usedQuantity(BigDecimal.ZERO);

                if (previousInventory != null) {
                    // 이전 달 데이터가 있으면 남은재고, 유효기간, 비고 이월
                    builder.initialStock(previousInventory.getRemainingStock() != null
                            ? previousInventory.getRemainingStock() : BigDecimal.ZERO)
                           .expiryDate(previousInventory.getExpiryDate())
                           .note(previousInventory.getNote());
                } else {
                    builder.initialStock(BigDecimal.ZERO);
                }

                inventoryRepository.save(builder.build());
            }
        }
    }

    private Inventory getPreviousMonthInventory(Long productId, String currentYearMonth) {
        YearMonth current = YearMonth.parse(currentYearMonth);
        YearMonth previous = current.minusMonths(1);
        String previousMonth = previous.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        return inventoryRepository.findByProductIdAndYearMonth(productId, previousMonth)
                .orElse(null);
    }

    @Transactional
    public void deleteInventory(Long id) {
        inventoryRepository.deleteById(id);
    }

    public List<InventoryDTO> getProductHistory(Long productId) {
        return inventoryRepository.findByProductIdOrderByYearMonthDesc(productId).stream()
                .map(InventoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * FIFO 방식으로 재고 차감
     */
    @Transactional
    public InventoryDTO deductStockWithFIFO(Long inventoryId, BigDecimal quantityToDeduct) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보를 찾을 수 없습니다: " + inventoryId));

        Long productId = inventory.getProduct().getId();

        // FIFO 차감 (유효기간 빠른 순으로)
        List<StockOrder> activeOrders = stockOrderRepository.findActiveExpiryByProductId(productId);
        BigDecimal remaining = quantityToDeduct;

        for (StockOrder order : activeOrders) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            // 수량이 없는 경우 (기존 데이터) - FIFO 대상에서 제외
            if (order.getRemainingQuantity() == null) continue;

            BigDecimal available = order.getRemainingQuantity();
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal toDeduct = remaining.min(available);
            order.setRemainingQuantity(available.subtract(toDeduct));

            // 남은수량이 0이면 소진완료 처리
            if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                order.setConsumed(true);
            }

            stockOrderRepository.save(order);
            remaining = remaining.subtract(toDeduct);

            activityLogService.logUpdate("ORDER", order.getId(), order.getProduct().getName(),
                    "FIFO 차감 - " + toDeduct + " (유효기간: " +
                            (order.getExpiryDate() != null ?
                                    order.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "미지정") + ")");
        }

        // Inventory 사용량 업데이트
        BigDecimal currentUsed = inventory.getUsedQuantity() != null ? inventory.getUsedQuantity() : BigDecimal.ZERO;
        inventory.setUsedQuantity(currentUsed.add(quantityToDeduct));

        // 남은재고 재계산
        BigDecimal addedStock = inventory.getAddedStock() != null ? inventory.getAddedStock() : BigDecimal.ZERO;
        BigDecimal initialStock = inventory.getInitialStock() != null ? inventory.getInitialStock() : BigDecimal.ZERO;
        inventory.setRemainingStock(initialStock.add(addedStock).subtract(inventory.getUsedQuantity()));

        Inventory saved = inventoryRepository.save(inventory);
        activityLogService.logUpdate("INVENTORY", saved.getId(), saved.getProduct().getName(),
                "재고 차감 - " + quantityToDeduct + " (FIFO)");

        return InventoryDTO.fromEntity(saved);
    }
}
