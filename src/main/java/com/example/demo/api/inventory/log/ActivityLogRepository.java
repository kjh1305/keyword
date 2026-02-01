package com.example.demo.api.inventory.log;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    Page<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT l FROM ActivityLog l WHERE " +
           "(:username IS NULL OR l.username LIKE CONCAT('%', :username, '%')) AND " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:targetType IS NULL OR l.targetType = :targetType) " +
           "ORDER BY l.createdAt DESC")
    Page<ActivityLog> searchLogs(@Param("username") String username,
                                  @Param("action") String action,
                                  @Param("targetType") String targetType,
                                  Pageable pageable);

    List<ActivityLog> findByCreatedAtBefore(LocalDateTime dateTime);

    long countByTargetType(String targetType);
}
