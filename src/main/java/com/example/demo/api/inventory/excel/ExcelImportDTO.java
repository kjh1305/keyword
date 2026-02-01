package com.example.demo.api.inventory.excel;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    private List<LocalDate> receivedDates;  // 입고일 (여러 개)
    private List<LocalDate> expiryDates;    // 유효기간 (여러 개)
    private String unit;
    private Integer minQuantity;
    private String note;

    // 단일 값 반환 (호환성 유지) - 가장 빠른 날짜
    public LocalDate getExpiryDate() {
        if (expiryDates == null || expiryDates.isEmpty()) return null;
        return expiryDates.stream().min(LocalDate::compareTo).orElse(null);
    }

    public LocalDate getReceivedDate() {
        if (receivedDates == null || receivedDates.isEmpty()) return null;
        return receivedDates.stream().min(LocalDate::compareTo).orElse(null);
    }
}
