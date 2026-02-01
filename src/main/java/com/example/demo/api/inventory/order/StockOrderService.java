package com.example.demo.api.inventory.order;

import com.example.demo.api.inventory.log.ActivityLogService;
import com.example.demo.api.inventory.product.Product;
import com.example.demo.api.inventory.product.ProductRepository;
import com.example.demo.api.inventory.stock.Inventory;
import com.example.demo.api.inventory.stock.InventoryRepository;
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
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockOrderService {

    private final StockOrderRepository stockOrderRepository;
    private final ProductRepository productRepository;
    private final ActivityLogService activityLogService;
    private final InventoryRepository inventoryRepository;

    public List<StockOrderDTO> getAllOrders() {
        return stockOrderRepository.findAllWithProduct().stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<StockOrderDTO> searchOrders(String category, String keyword, String status) {
        String cat = (category == null || category.isEmpty()) ? null : category;
        String kw = (keyword == null || keyword.isEmpty()) ? null : keyword;
        String st = (status == null || status.isEmpty()) ? null : status;

        return stockOrderRepository.searchOrders(cat, kw, st).stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<StockOrderDTO> searchOrdersPaged(String category, String keyword, String status,
                                                  LocalDate startDate, LocalDate endDate,
                                                  int page, int size) {
        String cat = (category == null || category.isEmpty()) ? null : category;
        String kw = (keyword == null || keyword.isEmpty()) ? null : keyword;
        String st = (status == null || status.isEmpty()) ? null : status;

        Pageable pageable = PageRequest.of(page, size);
        Page<StockOrder> orderPage = stockOrderRepository.searchOrdersPaged(cat, kw, st, startDate, endDate, pageable);

        return orderPage.map(StockOrderDTO::fromEntity);
    }

    public List<StockOrderDTO> getPendingOrders() {
        return stockOrderRepository.findByStatus("PENDING").stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<StockOrderDTO> getCompletedOrders() {
        return stockOrderRepository.findByStatus("COMPLETED").stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public StockOrderDTO getOrderById(Long id) {
        return stockOrderRepository.findByIdWithProduct(id)
                .map(StockOrderDTO::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다: " + id));
    }

    @Transactional
    public StockOrderDTO createOrder(StockOrderDTO dto) {
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다: " + dto.getProductId()));

        StockOrder order = StockOrder.builder()
                .product(product)
                .orderQuantity(dto.getOrderQuantity())
                .quantity(dto.getQuantity())
                .orderDate(dto.getOrderDate() != null ? dto.getOrderDate() : LocalDate.now())
                .status("PENDING")
                .expiryDate(dto.getExpiryDate())
                .note(dto.getNote())
                .build();

        StockOrder saved = stockOrderRepository.save(order);
        activityLogService.logCreate("ORDER", saved.getId(), product.getName() + " 주문");
        return StockOrderDTO.fromEntity(saved);
    }

    @Transactional
    public StockOrderDTO completeOrder(Long id, BigDecimal receivedQuantity, LocalDate expiryDate) {
        StockOrder order = stockOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다: " + id));

        if ("COMPLETED".equals(order.getStatus())) {
            throw new IllegalArgumentException("이미 입고 완료된 주문입니다.");
        }

        order.setStatus("COMPLETED");
        order.setReceivedDate(LocalDate.now());

        // 유효기간 설정 (입고 시 입력한 값 우선, 없으면 주문 시 입력한 값 사용)
        if (expiryDate != null) {
            order.setExpiryDate(expiryDate);
        }

        // 주문에 저장된 수량 사용 (파라미터로 받은 값이 없으면)
        BigDecimal quantityToAdd = receivedQuantity != null ? receivedQuantity : order.getQuantity();

        // 남은수량 초기화 (FIFO 차감용)
        order.setRemainingQuantity(quantityToAdd);
        order.setConsumed(false);

        // 현재 월의 재고에 추가재고 반영
        if (quantityToAdd != null && quantityToAdd.compareTo(BigDecimal.ZERO) > 0) {
            String currentYearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Long productId = order.getProduct().getId();

            Inventory inventory = inventoryRepository.findByProductIdAndYearMonth(productId, currentYearMonth)
                    .orElseGet(() -> {
                        Inventory newInv = Inventory.builder()
                                .product(order.getProduct())
                                .yearMonth(currentYearMonth)
                                .initialStock(BigDecimal.ZERO)
                                .addedStock(BigDecimal.ZERO)
                                .usedQuantity(BigDecimal.ZERO)
                                .build();
                        return inventoryRepository.save(newInv);
                    });

            BigDecimal currentAdded = inventory.getAddedStock() != null ? inventory.getAddedStock() : BigDecimal.ZERO;
            inventory.setAddedStock(currentAdded.add(quantityToAdd));

            // 남은재고 재계산
            BigDecimal remaining = inventory.getInitialStock()
                    .add(inventory.getAddedStock())
                    .subtract(inventory.getUsedQuantity());
            inventory.setRemainingStock(remaining);

            inventoryRepository.save(inventory);
        }

        StockOrder saved = stockOrderRepository.save(order);
        activityLogService.logUpdate("ORDER", saved.getId(), order.getProduct().getName(),
                "입고완료 - 수량: " + (quantityToAdd != null ? quantityToAdd : 0));
        return StockOrderDTO.fromEntity(saved);
    }

    @Transactional
    public StockOrderDTO updateOrder(Long id, StockOrderDTO dto) {
        StockOrder order = stockOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다: " + id));

        if (dto.getProductId() != null && !dto.getProductId().equals(order.getProduct().getId())) {
            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다: " + dto.getProductId()));
            order.setProduct(product);
        }

        if (dto.getOrderQuantity() != null) {
            order.setOrderQuantity(dto.getOrderQuantity());
        }
        if (dto.getQuantity() != null) {
            order.setQuantity(dto.getQuantity());
        }
        if (dto.getOrderDate() != null) {
            order.setOrderDate(dto.getOrderDate());
        }
        if (dto.getExpiryDate() != null) {
            order.setExpiryDate(dto.getExpiryDate());
        }
        order.setNote(dto.getNote());

        StockOrder saved = stockOrderRepository.save(order);
        activityLogService.logUpdate("ORDER", saved.getId(), order.getProduct().getName(), null);
        return StockOrderDTO.fromEntity(saved);
    }

    @Transactional
    public void deleteOrder(Long id) {
        StockOrder order = stockOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다: " + id));
        String productName = order.getProduct().getName();
        stockOrderRepository.deleteById(id);
        activityLogService.logDelete("ORDER", id, productName + " 주문");
    }

    public List<StockOrderDTO> getOrdersByProduct(Long productId) {
        return stockOrderRepository.findByProductId(productId).stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<StockOrderDTO> getExpiryHistoryByProduct(Long productId) {
        return stockOrderRepository.findCompletedWithExpiryByProductId(productId).stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getExpiryHistoryByProductPaged(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        // 모든 유효기간 항목 조회 (소진 여부 관계없이 - 관리용)
        Page<StockOrder> orderPage = stockOrderRepository.findAllExpiryByProductIdPaged(productId, pageable);

        List<StockOrderDTO> content = orderPage.getContent().stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("currentPage", orderPage.getNumber());
        result.put("totalPages", orderPage.getTotalPages());
        result.put("totalElements", orderPage.getTotalElements());
        result.put("hasNext", orderPage.hasNext());
        result.put("hasPrevious", orderPage.hasPrevious());

        return result;
    }

    /**
     * 소진완료 처리 (수동)
     */
    @Transactional
    public StockOrderDTO markAsConsumed(Long id) {
        StockOrder order = stockOrderRepository.findByIdWithProduct(id)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다: " + id));

        order.setConsumed(true);
        order.setRemainingQuantity(BigDecimal.ZERO);

        StockOrder saved = stockOrderRepository.save(order);
        activityLogService.logUpdate("ORDER", saved.getId(), order.getProduct().getName(),
                "유효기간 소진완료 처리 - " + (order.getExpiryDate() != null ?
                        order.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : ""));
        return StockOrderDTO.fromEntity(saved);
    }

    /**
     * 소진완료 취소
     */
    @Transactional
    public StockOrderDTO unmarkAsConsumed(Long id) {
        StockOrder order = stockOrderRepository.findByIdWithProduct(id)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다: " + id));

        order.setConsumed(false);
        // 남은수량 복원 (원래 수량으로)
        if (order.getQuantity() != null) {
            order.setRemainingQuantity(order.getQuantity());
        }

        StockOrder saved = stockOrderRepository.save(order);
        activityLogService.logUpdate("ORDER", saved.getId(), order.getProduct().getName(),
                "유효기간 소진완료 취소");
        return StockOrderDTO.fromEntity(saved);
    }

    /**
     * 수량 설정/수정
     */
    @Transactional
    public StockOrderDTO setQuantity(Long id, BigDecimal quantity) {
        StockOrder order = stockOrderRepository.findByIdWithProduct(id)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다: " + id));

        BigDecimal oldQuantity = order.getRemainingQuantity();
        order.setRemainingQuantity(quantity);

        // 입고수량도 함께 업데이트 (처음 설정하는 경우)
        if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            order.setQuantity(quantity);
        }

        // 수량이 0이면 소진완료, 아니면 미소진
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            order.setConsumed(true);
        } else {
            order.setConsumed(false);
        }

        StockOrder saved = stockOrderRepository.save(order);

        String logDetail = "유효기간 수량 수정 - " +
                (oldQuantity != null ? oldQuantity : "미지정") + " → " + quantity +
                " (유효기간: " + (order.getExpiryDate() != null ?
                        order.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "미지정") + ")";
        activityLogService.logUpdate("ORDER", saved.getId(), order.getProduct().getName(), logDetail);

        return StockOrderDTO.fromEntity(saved);
    }

    /**
     * 제품의 활성 유효기간 목록 조회 (소진되지 않은 것만, 유효기간 빠른 순)
     */
    public List<StockOrderDTO> getActiveExpiryDatesByProduct(Long productId) {
        return stockOrderRepository.findActiveExpiryByProductId(productId).stream()
                .map(StockOrderDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * FIFO 방식으로 재고 차감
     * @return 실제 차감된 수량
     */
    @Transactional
    public BigDecimal deductStockFIFO(Long productId, BigDecimal quantityToDeduct) {
        List<StockOrder> activeOrders = stockOrderRepository.findActiveExpiryByProductId(productId);

        BigDecimal remaining = quantityToDeduct;
        BigDecimal totalDeducted = BigDecimal.ZERO;

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
            totalDeducted = totalDeducted.add(toDeduct);
            remaining = remaining.subtract(toDeduct);

            activityLogService.logUpdate("ORDER", order.getId(), order.getProduct().getName(),
                    "FIFO 차감 - " + toDeduct + " (유효기간: " +
                            (order.getExpiryDate() != null ?
                                    order.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "미지정") + ")");
        }

        return totalDeducted;
    }
}
