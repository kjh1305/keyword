package com.example.demo.api.inventory.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(String category);

    Page<Product> findByNameContaining(String name, Pageable pageable);

    Page<Product> findByCategoryAndNameContaining(String category, String name, Pageable pageable);

    Page<Product> findByCategory(String category, Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    Optional<Product> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT p FROM Product p WHERE " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:keyword IS NULL OR p.name LIKE %:keyword%)")
    Page<Product> searchProducts(@Param("category") String category,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);
}
