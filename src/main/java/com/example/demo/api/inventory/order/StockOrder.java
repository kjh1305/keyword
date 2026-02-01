package com.example.demo.api.inventory.order;

import com.example.demo.api.inventory.product.Product;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Product product;

    @Column(name = "order_quantity", length = 50)
    private String orderQuantity;

    @Column(name = "quantity", precision = 10, scale = 1)
    private BigDecimal quantity;  // 실제 입고될 수량 (숫자)

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";  // PENDING: 입고대기, COMPLETED: 입고완료

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "remaining_quantity", precision = 10, scale = 1)
    private BigDecimal remainingQuantity;  // 남은 수량 (FIFO 차감용)

    @Column(name = "consumed")
    @Builder.Default
    private Boolean consumed = false;  // 소진완료 여부

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
