package com.example.demo.api.inventory.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {

    /**
     * 제품별 사용량 이력 조회 (최신순)
     */
    @Query("SELECT u FROM UsageLog u WHERE u.product.id = :productId ORDER BY u.createdAt DESC")
    List<UsageLog> findByProductId(@Param("productId") Long productId);

    /**
     * 제품별 사용량 이력 조회 - 페이징
     */
    @Query("SELECT u FROM UsageLog u WHERE u.product.id = :productId ORDER BY u.createdAt DESC")
    Page<UsageLog> findByProductIdPaged(@Param("productId") Long productId, Pageable pageable);

    /**
     * 제품 + 년월별 사용량 이력 조회 (actionDate가 yyyy-MM-dd이므로 LIKE로 년월 매칭)
     */
    @Query("SELECT u FROM UsageLog u WHERE u.product.id = :productId AND u.actionDate LIKE CONCAT(:yearMonth, '%') ORDER BY u.createdAt DESC")
    List<UsageLog> findByProductIdAndYearMonth(@Param("productId") Long productId, @Param("yearMonth") String yearMonth);

    /**
     * 기간별 사용량 이력 조회
     */
    @Query("SELECT u FROM UsageLog u WHERE u.createdAt BETWEEN :startDate AND :endDate ORDER BY u.createdAt DESC")
    List<UsageLog> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * 제품별 사용량 이력 삭제
     */
    void deleteByProductId(@Param("productId") Long productId);

    /**
     * 제품별 + 년월별 운영용 사용량 합계 (DEDUCT - RESTORE)
     * actionDate가 yyyy-MM-dd 형식이므로 yyyy-MM으로 시작하는 것을 찾음
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN u.action = 'DEDUCT' THEN u.quantity ELSE 0 END), 0) - " +
           "COALESCE(SUM(CASE WHEN u.action = 'RESTORE' THEN u.quantity ELSE 0 END), 0) " +
           "FROM UsageLog u WHERE u.product.id = :productId AND u.actionDate LIKE CONCAT(:yearMonth, '%')")
    BigDecimal sumOperationalUsedByProductIdAndYearMonth(@Param("productId") Long productId, @Param("yearMonth") String yearMonth);

    /**
     * 제품 + 날짜 범위별 사용량 이력 조회 (actionDate가 yyyy-MM-dd 문자열이므로 범위 비교)
     */
    @Query("SELECT u FROM UsageLog u WHERE u.product.id = :productId AND u.actionDate >= :startDate AND u.actionDate <= :endDate ORDER BY u.createdAt DESC")
    List<UsageLog> findByProductIdAndDateRangeStr(@Param("productId") Long productId,
                                                    @Param("startDate") String startDate,
                                                    @Param("endDate") String endDate);

    /**
     * 제품별 날짜 범위 운영용 사용량 합계 (DEDUCT - RESTORE)
     * actionDate가 yyyy-MM-dd 문자열이므로 문자열 비교
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN u.action = 'DEDUCT' THEN u.quantity ELSE 0 END), 0) - " +
           "COALESCE(SUM(CASE WHEN u.action = 'RESTORE' THEN u.quantity ELSE 0 END), 0) " +
           "FROM UsageLog u WHERE u.product.id = :productId AND u.actionDate >= :startDate AND u.actionDate <= :endDate")
    BigDecimal sumOperationalUsedByProductIdAndDateRange(@Param("productId") Long productId,
                                                         @Param("startDate") String startDate,
                                                         @Param("endDate") String endDate);
}
