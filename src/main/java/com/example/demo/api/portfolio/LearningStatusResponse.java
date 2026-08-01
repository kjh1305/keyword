package com.example.demo.api.portfolio;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * 포트폴리오 학습 기록 응답. 날짜는 문자열로 포맷해서 내려준다 —
 * 앱 전역 Jackson 설정이 LocalDateTime을 배열([y,m,d,...])로 직렬화하기 때문에
 * 프런트가 파싱 가능한 형태를 여기서 보장한다.
 */
public record LearningStatusResponse(
        Long courseId,
        String courseTitle,
        BigDecimal progress,
        Integer completeCount,
        Integer lectureCount,
        String categories,
        String accessedDate,
        String syncedAt
) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static LearningStatusResponse from(LearningStatus s) {
        return new LearningStatusResponse(
                s.getCourseId(),
                s.getCourseTitle(),
                s.getProgress(),
                s.getCompleteCount(),
                s.getLectureCount(),
                s.getCategories(),
                s.getAccessedAt() != null ? s.getAccessedAt().format(DATE) : null,
                s.getSyncedAt() != null ? s.getSyncedAt().format(MINUTE) : null
        );
    }
}
