package com.example.demo.api.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 인프런 수강 현황. 서버 cron(Claude Code + 인프런 MCP)이 매일 upsert하고,
 * 포트폴리오 페이지(/portfolio)의 학습 기록 섹션이 조회한다.
 * 스키마 변경 시 서버의 inflearn-sync 스크립트(CREATE TABLE/INSERT)도 함께 맞출 것.
 */
@Entity
@Table(name = "learning_status")
@Getter
@Setter
@NoArgsConstructor
public class LearningStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false, unique = true)
    private Long courseId;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @Column(precision = 5, scale = 2)
    private BigDecimal progress;

    @Column(name = "complete_count")
    private Integer completeCount;

    @Column(name = "lecture_count")
    private Integer lectureCount;

    private String categories;

    @Column(name = "accessed_at")
    private LocalDateTime accessedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
