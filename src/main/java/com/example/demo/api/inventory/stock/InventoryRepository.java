package com.example.demo.api.inventory.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findByYearMonth(String yearMonth);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.yearMonth = :yearMonth ORDER BY i.product.category, i.product.name")
    List<Inventory> findByYearMonthWithProduct(@Param("yearMonth") String yearMonth);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product p WHERE i.yearMonth = :yearMonth " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR p.name LIKE %:keyword%) " +
           "ORDER BY p.category, p.name")
    List<Inventory> searchInventory(@Param("yearMonth") String yearMonth,
                                     @Param("category") String category,
                                     @Param("keyword") String keyword);

    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product p WHERE i.yearMonth = :yearMonth " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR p.name LIKE %:keyword%) " +
           "ORDER BY p.category, p.name",
           countQuery = "SELECT COUNT(i) FROM Inventory i JOIN i.product p WHERE i.yearMonth = :yearMonth " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:keyword IS NULL OR p.name LIKE %:keyword%)")
    Page<Inventory> searchInventoryPaged(@Param("yearMonth") String yearMonth,
                                          @Param("category") String category,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);

    Optional<Inventory> findByProductIdAndYearMonth(Long productId, String yearMonth);

    @Query("SELECT DISTINCT i.yearMonth FROM Inventory i ORDER BY i.yearMonth DESC")
    List<String> findAllYearMonths();

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.product.id = :productId ORDER BY i.yearMonth DESC")
    List<Inventory> findByProductIdOrderByYearMonthDesc(@Param("productId") Long productId);

    boolean existsByProductIdAndYearMonth(Long productId, String yearMonth);
}
