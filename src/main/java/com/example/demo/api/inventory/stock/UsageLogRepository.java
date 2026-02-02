package com.example.demo.api.inventory.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
