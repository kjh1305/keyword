package com.example.demo.api.inventory.order;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockOrderDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productCategory;
    private String orderQuantity;
    private BigDecimal quantity;  // 실제 입고 수량 (숫자)
    private LocalDate orderDate;
    private String orderDateStr;
    private String status;
    private String statusText;
    private LocalDate receivedDate;
    private String receivedDateStr;
    private LocalDate expiryDate;
    private String expiryDateStr;
    private Long daysUntilExpiry;  // 유효기간까지 남은 일수
    private String expiryStatus;   // EXPIRED, URGENT(7일이내), WARNING(30일이내), NORMAL
    private String note;

    public static StockOrderDTO fromEntity(StockOrder order) {
        StockOrderDTO dto = StockOrderDTO.builder()
                .id(order.getId())
                .productId(order.getProduct().getId())
                .productName(order.getProduct().getName())
                .productCategory(order.getProduct().getCategory())
                .orderQuantity(order.getOrderQuantity())
                .quantity(order.getQuantity())
                .orderDate(order.getOrderDate())
                .status(order.getStatus())
                .receivedDate(order.getReceivedDate())
                .expiryDate(order.getExpiryDate())
                .note(order.getNote())
                .build();

        if (order.getOrderDate() != null) {
            dto.setOrderDateStr(order.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        if (order.getReceivedDate() != null) {
            dto.setReceivedDateStr(order.getReceivedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        if (order.getExpiryDate() != null) {
            dto.setExpiryDateStr(order.getExpiryDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            // 유효기간까지 남은 일수 계산
            long days = ChronoUnit.DAYS.between(LocalDate.now(), order.getExpiryDate());
            dto.setDaysUntilExpiry(days);
            // 상태 결정
            if (days < 0) {
                dto.setExpiryStatus("EXPIRED");
            } else if (days <= 7) {
                dto.setExpiryStatus("URGENT");
            } else if (days <= 30) {
                dto.setExpiryStatus("WARNING");
            } else {
                dto.setExpiryStatus("NORMAL");
            }
        }

        // 상태 텍스트 변환
        if ("PENDING".equals(order.getStatus())) {
            dto.setStatusText("입고대기");
        } else if ("COMPLETED".equals(order.getStatus())) {
            dto.setStatusText("입고완료");
        }

        return dto;
    }
}
