package com.example.demo.api.inventory.stock;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageLogDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String actionDate;  // yyyy-MM-dd
    private String action;
    private String actionLabel;  // 차감/복구
    private BigDecimal quantity;
    private BigDecimal beforeUsed;
    private BigDecimal afterUsed;
    private BigDecimal beforeRemaining;
    private BigDecimal afterRemaining;
    private String username;
    private String note;
    private LocalDateTime createdAt;
    private String createdAtStr;

    public static UsageLogDTO fromEntity(UsageLog log) {
        return UsageLogDTO.builder()
                .id(log.getId())
                .productId(log.getProduct().getId())
                .productName(log.getProduct().getName())
                .actionDate(log.getActionDate())
                .action(log.getAction())
                .actionLabel("DEDUCT".equals(log.getAction()) ? "차감" : "복구")
                .quantity(log.getQuantity())
                .beforeUsed(log.getBeforeUsed())
                .afterUsed(log.getAfterUsed())
                .beforeRemaining(log.getBeforeRemaining())
                .afterRemaining(log.getAfterRemaining())
                .username(log.getUsername())
                .note(log.getNote())
                .createdAt(log.getCreatedAt())
                .createdAtStr(log.getCreatedAt() != null ?
                        log.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : null)
                .build();
    }
}
