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
        return stockOrderRepository.findById(id)
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
        Page<StockOrder> orderPage = stockOrderRepository.findValidExpiryByProductId(
                productId, LocalDate.now(), pageable);

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
}
