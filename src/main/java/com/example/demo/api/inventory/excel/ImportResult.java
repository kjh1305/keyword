package com.example.demo.api.inventory.excel;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResult {
    @Builder.Default
    private int newProducts = 0;
    @Builder.Default
    private int updatedProducts = 0;
    @Builder.Default
    private int skippedProducts = 0;
    @Builder.Default
    private int inventoryRecords = 0;
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("신규 제품: ").append(newProducts).append("개, ");
        sb.append("업데이트: ").append(updatedProducts).append("개, ");
        sb.append("스킵: ").append(skippedProducts).append("개, ");
        sb.append("재고 기록: ").append(inventoryRecords).append("건");
        if (!errors.isEmpty()) {
            sb.append(", 오류: ").append(errors.size()).append("건");
        }
        return sb.toString();
    }
}
