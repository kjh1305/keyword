package com.example.demo.api.inventory.excel;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelImportDTO {
    private Integer rowNumber;
    private String productName;
    private String category;
    private BigDecimal initialStock;
    private BigDecimal usedQuantity;
    private LocalDate expiryDate;
    private String unit;
    private Integer minQuantity;
    private String note;
}
