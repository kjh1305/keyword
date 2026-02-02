package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.product.ProductDTO;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productCategory;
    private String productUnit;
    private String yearMonth;
    private BigDecimal initialStock;
    private BigDecimal addedStock;          // 기존 필드 유지 (DB 호환)
    private BigDecimal pendingStock;        // 입고대기 수량
    private BigDecimal completedStock;      // 입고완료 수량
    private BigDecimal usedQuantity;           // 보고용 사용량 (엑셀 업로드 데이터)
    private BigDecimal currentMonthUsedQuantity; // 운영용 사용량 (UsageLog 기반, 실제 차감량)
    private BigDecimal remainingStock;
    private LocalDate expiryDate;
    private String expiryDateStr;
    private List<String> expiryDates;  // 여러 유효기간 목록 (표시용)
    private List<ExpiryInfo> expiryInfoList;  // 유효기간별 상세정보 (FIFO용)
    private String note;

    /**
     * 유효기간 상세 정보
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExpiryInfo {
        private Long orderId;           // StockOrder ID
        private String expiryDateStr;   // 유효기간 (표시용)
        private LocalDate expiryDate;   // 유효기간
        private BigDecimal quantity;    // 입고수량
        private BigDecimal remainingQuantity;  // 남은수량
        private Boolean consumed;       // 소진완료 여부
        private boolean hasQuantity;    // 수량 정보 유무
    }
    private Integer minQuantity;
    private boolean lowStock;
    private boolean expiryWarning;  // 유효기간 30일 이내 임박

    public static InventoryDTO fromEntity(Inventory inventory) {
        InventoryDTO dto = InventoryDTO.builder()
                .id(inventory.getId())
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .productCategory(inventory.getProduct().getCategory())
                .productUnit(inventory.getProduct().getUnit())
                .yearMonth(inventory.getYearMonth())
                .initialStock(inventory.getInitialStock())
                .addedStock(inventory.getAddedStock())
                .usedQuantity(inventory.getUsedQuantity())
                .remainingStock(inventory.getRemainingStock())
                .expiryDate(inventory.getExpiryDate())
                .note(inventory.getNote())
                .minQuantity(inventory.getProduct().getMinQuantity())
                .build();

        if (inventory.getExpiryDate() != null) {
            dto.setExpiryDateStr(inventory.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")));
        }

        if (inventory.getProduct().getMinQuantity() != null && inventory.getRemainingStock() != null) {
            dto.setLowStock(inventory.getRemainingStock().compareTo(
                    BigDecimal.valueOf(inventory.getProduct().getMinQuantity())) <= 0);
        }

        return dto;
    }
}
