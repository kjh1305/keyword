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
    private final UsageLogRepository usageLogRepository;

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

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysFromNow = today.plusDays(30);

        // 선택한 월과 이전 달 계산
        // yearMonth = 선택한 달 (예: 2025-02) → 주문 조회용
        // prevYearMonth = 이전 달 (예: 2025-01) → 재고 조회용
        String prevYearMonth = yearMonth;
        int orderYear = 0;
        int orderMonth = 0;
        if (yearMonth != null && !yearMonth.isEmpty()) {
            YearMonth selectedYM = YearMonth.parse(yearMonth);
            YearMonth prevYM = selectedYM.minusMonths(1);  // 재고는 이전 달 기준
            prevYearMonth = prevYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            orderYear = selectedYM.getYear();  // 주문은 선택한 달 기준
            orderMonth = selectedYM.getMonthValue();
        }
        final int finalOrderYear = orderYear;
        final int finalOrderMonth = orderMonth;

        // 재고 데이터는 이전 달 기준으로 조회 (2월 선택 시 1월 재고)
        List<Inventory> allInventories = inventoryRepository.searchInventory(prevYearMonth, cat, kw);

        List<InventoryDTO> allDtos = allInventories.stream()
                .map(inv -> {
                    InventoryDTO dto = InventoryDTO.fromEntity(inv);
                    Long productId = inv.getProduct().getId();

                    // 주문재고: 해당 월 기준 입고대기/입고완료 수량
                    BigDecimal pendingStock = BigDecimal.ZERO;
                    BigDecimal completedStock = BigDecimal.ZERO;
                    if (finalOrderYear > 0 && finalOrderMonth > 0) {
                        pendingStock = stockOrderRepository.sumPendingQuantityByProductIdAndMonth(productId, finalOrderYear, finalOrderMonth);
                        completedStock = stockOrderRepository.sumCompletedQuantityByProductIdAndMonth(productId, finalOrderYear, finalOrderMonth);
                    }
                    dto.setPendingStock(pendingStock != null ? pendingStock : BigDecimal.ZERO);
                    dto.setCompletedStock(completedStock != null ? completedStock : BigDecimal.ZERO);

                    // 운영용 사용량: UsageLog 기반 (실제 차감량, 선택한 월 기준)
                    BigDecimal operationalUsed = BigDecimal.ZERO;
                    if (yearMonth != null && !yearMonth.isEmpty()) {
                        operationalUsed = usageLogRepository.sumOperationalUsedByProductIdAndYearMonth(productId, yearMonth);
                        if (operationalUsed == null) operationalUsed = BigDecimal.ZERO;
                    }
                    dto.setCurrentMonthUsedQuantity(operationalUsed);

                    // 남은재고 재계산: 월초재고 + 입고완료 - 전월사용량(보고용) - 당월사용량(운영용)
                    BigDecimal initialStock = dto.getInitialStock() != null ? dto.getInitialStock() : BigDecimal.ZERO;
                    BigDecimal completed = dto.getCompletedStock() != null ? dto.getCompletedStock() : BigDecimal.ZERO;
                    BigDecimal reportUsed = dto.getUsedQuantity() != null ? dto.getUsedQuantity() : BigDecimal.ZERO;
                    BigDecimal newRemainingStock = initialStock.add(completed).subtract(reportUsed).subtract(operationalUsed);
                    dto.setRemainingStock(newRemainingStock);

                    // lowStock 재계산: 재계산된 remainingStock 기준으로 (0 < 남은재고 <= 최소수량)
                    BigDecimal minQty = dto.getMinQuantity() != null ? new BigDecimal(dto.getMinQuantity()) : BigDecimal.ZERO;
                    dto.setLowStock(newRemainingStock.compareTo(BigDecimal.ZERO) > 0 && newRemainingStock.compareTo(minQty) <= 0);

                    // StockOrder에서 해당 제품의 유효기간 목록 가져오기 (유효기간 빠른 순)
                    List<StockOrder> orders = stockOrderRepository.findAllExpiryByProductId(productId);

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

        // 통계 계산 (필터 적용 전 전체 데이터 기준)
        long outOfStockCount = allDtos.stream()
                .filter(dto -> dto.getRemainingStock() != null && dto.getRemainingStock().compareTo(BigDecimal.ZERO) <= 0)
                .count();

        long lowStockCount = allDtos.stream()
                .filter(dto -> {
                    BigDecimal remaining = dto.getRemainingStock() != null ? dto.getRemainingStock() : BigDecimal.ZERO;
                    BigDecimal minQty = dto.getMinQuantity() != null ? new BigDecimal(dto.getMinQuantity()) : BigDecimal.ZERO;
                    return remaining.compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(minQty) <= 0;
                })
                .count();

        long expiryWarningCount = allDtos.stream()
                .filter(InventoryDTO::isExpiryWarning)
                .count();

        // stockFilter 적용
        List<InventoryDTO> filteredDtos = allDtos;
        if (filter != null) {
            filteredDtos = allDtos.stream()
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

        // 필터 적용 후 페이지네이션
        int totalElements = filteredDtos.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<InventoryDTO> content;
        if (fromIndex >= totalElements) {
            content = new ArrayList<>();
        } else {
            content = filteredDtos.subList(fromIndex, toIndex);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("currentPage", page);
        result.put("totalPages", totalPages);
        result.put("totalElements", totalElements);  // 필터 적용 후 개수 (페이지네이션용)
        result.put("totalProductCount", allDtos.size());  // 전체 제품 개수 (필터 무관)
        result.put("hasNext", page < totalPages - 1);
        result.put("hasPrevious", page > 0);
        result.put("outOfStockCount", outOfStockCount);
        result.put("lowStockCount", lowStockCount);
        result.put("expiryWarningCount", expiryWarningCount);

        return result;
    }

    public List<String> getAllYearMonths() {
        List<String> dbMonths = inventoryRepository.findAllYearMonths();

        // DB에 있는 월들만 표시 (다음 달 자동 추가 제거)
        java.util.Set<String> monthSet = new java.util.TreeSet<>(dbMonths);

        // 현재 월도 추가 (새 달 생성 전에도 현재 월은 선택 가능)
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        monthSet.add(currentMonth);

        List<String> result = new ArrayList<>(monthSet);
        java.util.Collections.reverse(result);  // 최신순 정렬
        return result;
    }

    public String getCurrentYearMonth() {
        return YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public String getCurrentDate() {
        return java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
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

        // 월 계산
        // yearMonth = 생성할 달 (예: 2026-02)
        // prevYearMonth = 이전 달 (예: 2026-01) - 이전 데이터 참조용
        YearMonth targetYM = YearMonth.parse(yearMonth);
        YearMonth prevYM = targetYM.minusMonths(1);
        String prevYearMonth = prevYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        for (Product product : allProducts) {
            Long productId = product.getId();

            // 이미 해당 월 데이터가 있으면 스킵
            if (inventoryRepository.existsByProductIdAndYearMonth(productId, yearMonth)) {
                continue;
            }

            // 이전 달 Inventory 조회
            Inventory prevInventory = inventoryRepository.findByProductIdAndYearMonth(productId, prevYearMonth)
                    .orElse(null);

            // 이전 달 데이터 기반으로 월초재고 계산
            BigDecimal prevInitialStock = BigDecimal.ZERO;
            BigDecimal prevUsedQuantity = BigDecimal.ZERO;
            if (prevInventory != null) {
                prevInitialStock = prevInventory.getInitialStock() != null
                        ? prevInventory.getInitialStock() : BigDecimal.ZERO;
                prevUsedQuantity = prevInventory.getUsedQuantity() != null
                        ? prevInventory.getUsedQuantity() : BigDecimal.ZERO;
            }

            // 이전 달 입고완료 수량
            BigDecimal prevCompletedStock = stockOrderRepository.sumCompletedQuantityByProductIdAndMonth(
                    productId, prevYM.getYear(), prevYM.getMonthValue());
            if (prevCompletedStock == null) prevCompletedStock = BigDecimal.ZERO;

            // 이전 달 운영 사용량 (UsageLog 기반)
            BigDecimal prevOperationalUsed = usageLogRepository.sumOperationalUsedByProductIdAndYearMonth(
                    productId, prevYearMonth);
            if (prevOperationalUsed == null) prevOperationalUsed = BigDecimal.ZERO;

            // 새 달 월초재고 = 이전 달 월초재고 + 이전 달 입고 - 이전 달 사용량(보고용) - 이전 달 운영 사용량
            BigDecimal newInitialStock = prevInitialStock.add(prevCompletedStock)
                    .subtract(prevUsedQuantity).subtract(prevOperationalUsed);

            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .yearMonth(yearMonth)
                    .initialStock(newInitialStock)
                    .usedQuantity(BigDecimal.ZERO)
                    .remainingStock(newInitialStock)
                    .expiryDate(prevInventory != null ? prevInventory.getExpiryDate() : null)
                    .note(prevInventory != null ? prevInventory.getNote() : null)
                    .build();

            inventoryRepository.save(newInventory);
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

        // 변경 전 값 저장
        BigDecimal beforeUsed = inventory.getUsedQuantity() != null ? inventory.getUsedQuantity() : BigDecimal.ZERO;
        BigDecimal beforeRemaining = inventory.getRemainingStock() != null ? inventory.getRemainingStock() : BigDecimal.ZERO;

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

        // 운영용 사용량은 UsageLog로만 관리 (Inventory.usedQuantity는 보고용으로 유지)
        // 남은재고는 조회 시 UsageLog 기반으로 계산되므로 여기서는 업데이트하지 않음

        // 사용량 이력 저장 (차감 시점의 현재 일자로 저장)
        UsageLog usageLog = UsageLog.builder()
                .product(inventory.getProduct())
                .actionDate(getCurrentDate())
                .action("DEDUCT")
                .quantity(quantityToDeduct)
                .beforeUsed(beforeUsed)
                .afterUsed(beforeUsed.add(quantityToDeduct))
                .beforeRemaining(beforeRemaining)
                .afterRemaining(beforeRemaining.subtract(quantityToDeduct))
                .username(getCurrentUsername())
                .build();
        usageLogRepository.save(usageLog);

        activityLogService.logUpdate("INVENTORY", inventory.getId(), inventory.getProduct().getName(),
                "재고 차감 - " + quantityToDeduct + " (FIFO)");

        return InventoryDTO.fromEntity(inventory);
    }

    /**
     * 재고 복구 (역FIFO - 유효기간 늦은 순으로)
     */
    @Transactional
    public InventoryDTO restoreStock(Long inventoryId, BigDecimal quantityToRestore) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("재고 정보를 찾을 수 없습니다: " + inventoryId));

        Long productId = inventory.getProduct().getId();

        // 변경 전 값 저장
        BigDecimal beforeUsed = inventory.getUsedQuantity() != null ? inventory.getUsedQuantity() : BigDecimal.ZERO;
        BigDecimal beforeRemaining = inventory.getRemainingStock() != null ? inventory.getRemainingStock() : BigDecimal.ZERO;

        // 역FIFO 복구 (유효기간 늦은 순으로, 소진된 것 포함)
        List<StockOrder> orders = stockOrderRepository.findAllExpiryByProductIdDesc(productId);
        BigDecimal remaining = quantityToRestore;

        for (StockOrder order : orders) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            // 수량이 없는 경우 제외
            if (order.getQuantity() == null) continue;

            BigDecimal currentRemaining = order.getRemainingQuantity() != null ? order.getRemainingQuantity() : BigDecimal.ZERO;
            BigDecimal maxQuantity = order.getQuantity();
            BigDecimal canRestore = maxQuantity.subtract(currentRemaining);

            if (canRestore.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal toRestore = remaining.min(canRestore);
            order.setRemainingQuantity(currentRemaining.add(toRestore));

            // 복구 후 남은수량이 있으면 소진완료 해제
            if (order.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                order.setConsumed(false);
            }

            stockOrderRepository.save(order);
            remaining = remaining.subtract(toRestore);

            activityLogService.logUpdate("ORDER", order.getId(), order.getProduct().getName(),
                    "재고 복구 - " + toRestore + " (유효기간: " +
                            (order.getExpiryDate() != null ?
                                    order.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "미지정") + ")");
        }

        // 운영용 사용량은 UsageLog로만 관리 (Inventory.usedQuantity는 보고용으로 유지)
        // 남은재고는 조회 시 UsageLog 기반으로 계산되므로 여기서는 업데이트하지 않음

        // 사용량 이력 저장 (복구 시점의 현재 일자로 저장)
        BigDecimal newUsed = beforeUsed.subtract(quantityToRestore);
        if (newUsed.compareTo(BigDecimal.ZERO) < 0) {
            newUsed = BigDecimal.ZERO;
        }

        UsageLog usageLog = UsageLog.builder()
                .product(inventory.getProduct())
                .actionDate(getCurrentDate())
                .action("RESTORE")
                .quantity(quantityToRestore)
                .beforeUsed(beforeUsed)
                .afterUsed(newUsed)
                .beforeRemaining(beforeRemaining)
                .afterRemaining(beforeRemaining.add(quantityToRestore))
                .username(getCurrentUsername())
                .build();
        usageLogRepository.save(usageLog);

        activityLogService.logUpdate("INVENTORY", inventory.getId(), inventory.getProduct().getName(),
                "재고 복구 - " + quantityToRestore);

        return InventoryDTO.fromEntity(inventory);
    }

    /**
     * 제품별 + 년월별 사용량 이력 조회
     */
    public List<UsageLogDTO> getUsageLogsByProductIdAndYearMonth(Long productId, String yearMonth) {
        return usageLogRepository.findByProductIdAndYearMonth(productId, yearMonth).stream()
                .map(UsageLogDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 현재 사용자명 조회
     */
    private String getCurrentUsername() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception e) {
            // ignore
        }
        return "SYSTEM";
    }
}
