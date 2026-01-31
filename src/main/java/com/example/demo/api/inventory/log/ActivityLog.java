package com.example.demo.api.inventory.log;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;  // 수행한 사용자

    @Column(nullable = false, length = 20)
    private String action;  // CREATE, UPDATE, DELETE

    @Column(nullable = false, length = 50)
    private String targetType;  // PRODUCT, INVENTORY, ORDER, USER

    @Column(name = "target_id")
    private Long targetId;  // 대상 ID

    @Column(name = "target_name", length = 200)
    private String targetName;  // 대상 이름 (제품명, 사용자명 등)

    @Column(columnDefinition = "TEXT")
    private String detail;  // 상세 내용

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
