package com.example.demo.api.inventory.stock;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_period")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
