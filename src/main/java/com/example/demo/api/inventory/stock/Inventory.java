package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.product.Product;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory",
       uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "ym"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Product product;

    @Column(name = "ym", nullable = false, length = 7)
    private String yearMonth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_period_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ReportPeriod reportPeriod;

    @Column(name = "initial_stock", precision = 10, scale = 1)
    @Builder.Default
    private BigDecimal initialStock = BigDecimal.ZERO;

    @Column(name = "added_stock", precision = 10, scale = 1)
    @Builder.Default
    private BigDecimal addedStock = BigDecimal.ZERO;

    @Column(name = "used_quantity", precision = 10, scale = 1)
    @Builder.Default
    private BigDecimal usedQuantity = BigDecimal.ZERO;

    @Column(name = "remaining_stock", precision = 10, scale = 1)
    @Builder.Default
    private BigDecimal remainingStock = BigDecimal.ZERO;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    protected void calculateRemaining() {
        // 남은재고 = 월초재고 + 추가재고 - 사용량
        this.remainingStock = this.initialStock
                .add(this.addedStock != null ? this.addedStock : BigDecimal.ZERO)
                .subtract(this.usedQuantity);
        this.updatedAt = LocalDateTime.now();
    }
}
