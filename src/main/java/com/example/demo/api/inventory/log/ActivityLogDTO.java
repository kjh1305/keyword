package com.example.demo.api.inventory.log;

import lombok.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLogDTO {
    private Long id;
    private String username;
    private String action;
    private String actionText;
    private String targetType;
    private String targetTypeText;
    private Long targetId;
    private String targetName;
    private String detail;
    private String ipAddress;
    private LocalDateTime createdAt;
    private String createdAtStr;

    public static ActivityLogDTO fromEntity(ActivityLog log) {
        ActivityLogDTO dto = ActivityLogDTO.builder()
                .id(log.getId())
                .username(log.getUsername())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .targetName(log.getTargetName())
                .detail(log.getDetail())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();

        // Action text
        switch (log.getAction()) {
            case "CREATE" -> dto.setActionText("추가");
            case "UPDATE" -> dto.setActionText("수정");
            case "DELETE" -> dto.setActionText("삭제");
            default -> dto.setActionText(log.getAction());
        }

        // Target type text
        switch (log.getTargetType()) {
            case "PRODUCT" -> dto.setTargetTypeText("상품");
            case "INVENTORY" -> dto.setTargetTypeText("재고");
            case "ORDER" -> dto.setTargetTypeText("주문/입고");
            case "USER" -> dto.setTargetTypeText("회원");
            case "SYSTEM" -> dto.setTargetTypeText("시스템");
            default -> dto.setTargetTypeText(log.getTargetType());
        }

        if (log.getCreatedAt() != null) {
            dto.setCreatedAtStr(log.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        return dto;
    }
}
