package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.product.ProductDTO;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    private BigDecimal addedStock;
    private BigDecimal usedQuantity;
    private BigDecimal remainingStock;
    private LocalDate expiryDate;
    private String expiryDateStr;
    private String note;
    private Integer minQuantity;
    private boolean lowStock;

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
