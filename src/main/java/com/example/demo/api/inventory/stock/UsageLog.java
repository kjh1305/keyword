package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.product.Product;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "action_date", length = 10)
    private String actionDate;  // 차감/복구 일자 (yyyy-MM-dd)

    @Column(name = "action_type", nullable = false, length = 20)
    private String action;  // DEDUCT (차감), RESTORE (복구)

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;  // 변경 수량

    @Column(name = "before_used", precision = 10, scale = 2)
    private BigDecimal beforeUsed;  // 변경 전 사용량

    @Column(name = "after_used", precision = 10, scale = 2)
    private BigDecimal afterUsed;  // 변경 후 사용량

    @Column(name = "before_remaining", precision = 10, scale = 2)
    private BigDecimal beforeRemaining;  // 변경 전 남은재고

    @Column(name = "after_remaining", precision = 10, scale = 2)
    private BigDecimal afterRemaining;  // 변경 후 남은재고

    @Column(length = 50)
    private String username;  // 수행한 사용자

    @Column(columnDefinition = "TEXT")
    private String note;  // 비고

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
