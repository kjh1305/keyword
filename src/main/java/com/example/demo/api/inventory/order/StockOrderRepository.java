package com.example.demo.api.inventory.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockOrderRepository extends JpaRepository<StockOrder, Long> {

    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product WHERE o.id = :id")
    java.util.Optional<StockOrder> findByIdWithProduct(@Param("id") Long id);

    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product ORDER BY o.createdAt DESC")
    List<StockOrder> findAllWithProduct();

    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product p " +
           "WHERE (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR p.name LIKE %:keyword%) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "ORDER BY o.createdAt DESC")
    List<StockOrder> searchOrders(@Param("category") String category,
                                   @Param("keyword") String keyword,
                                   @Param("status") String status);

    @Query(value = "SELECT o FROM StockOrder o JOIN FETCH o.product p " +
           "WHERE (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR p.name LIKE %:keyword%) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:startDate IS NULL OR o.orderDate >= :startDate) " +
           "AND (:endDate IS NULL OR o.orderDate <= :endDate) " +
           "ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM StockOrder o JOIN o.product p " +
           "WHERE (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR p.name LIKE %:keyword%) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:startDate IS NULL OR o.orderDate >= :startDate) " +
           "AND (:endDate IS NULL OR o.orderDate <= :endDate)")
    Page<StockOrder> searchOrdersPaged(@Param("category") String category,
                                        @Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        Pageable pageable);

    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product WHERE o.status = :status ORDER BY o.createdAt DESC")
    List<StockOrder> findByStatus(@Param("status") String status);

    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product WHERE o.product.id = :productId ORDER BY o.createdAt DESC")
    List<StockOrder> findByProductId(@Param("productId") Long productId);

    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product " +
           "WHERE o.product.id = :productId AND o.status = 'COMPLETED' AND o.expiryDate IS NOT NULL " +
           "ORDER BY o.expiryDate ASC")
    List<StockOrder> findCompletedWithExpiryByProductId(@Param("productId") Long productId);

    @Query(value = "SELECT o FROM StockOrder o JOIN FETCH o.product " +
           "WHERE o.product.id = :productId AND o.status = 'COMPLETED' " +
           "AND o.expiryDate IS NOT NULL AND o.expiryDate >= :today " +
           "ORDER BY o.expiryDate ASC",
           countQuery = "SELECT COUNT(o) FROM StockOrder o " +
           "WHERE o.product.id = :productId AND o.status = 'COMPLETED' " +
           "AND o.expiryDate IS NOT NULL AND o.expiryDate >= :today")
    Page<StockOrder> findValidExpiryByProductId(@Param("productId") Long productId,
                                                 @Param("today") LocalDate today,
                                                 Pageable pageable);

    /**
     * 활성 유효기간 목록 조회 (소진되지 않은 것만, 유효기간 빠른 순 - FIFO용)
     */
    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product " +
           "WHERE o.product.id = :productId AND o.status = 'COMPLETED' " +
           "AND o.expiryDate IS NOT NULL " +
           "AND (o.consumed IS NULL OR o.consumed = false) " +
           "ORDER BY o.expiryDate ASC")
    List<StockOrder> findActiveExpiryByProductId(@Param("productId") Long productId);

    /**
     * 제품의 모든 유효기간 목록 조회 (소진 여부 관계없이, 유효기간 빠른 순)
     */
    @Query("SELECT o FROM StockOrder o JOIN FETCH o.product " +
           "WHERE o.product.id = :productId AND o.status = 'COMPLETED' " +
           "AND o.expiryDate IS NOT NULL " +
           "ORDER BY o.expiryDate ASC")
    List<StockOrder> findAllExpiryByProductId(@Param("productId") Long productId);

    /**
     * 제품의 모든 유효기간 목록 조회 - 페이징 (관리용)
     */
    @Query(value = "SELECT o FROM StockOrder o JOIN FETCH o.product " +
           "WHERE o.product.id = :productId AND o.status = 'COMPLETED' " +
           "AND o.expiryDate IS NOT NULL " +
           "ORDER BY o.consumed ASC, o.expiryDate ASC",
           countQuery = "SELECT COUNT(o) FROM StockOrder o " +
           "WHERE o.product.id = :productId AND o.status = 'COMPLETED' " +
           "AND o.expiryDate IS NOT NULL")
    Page<StockOrder> findAllExpiryByProductIdPaged(@Param("productId") Long productId, Pageable pageable);
}
