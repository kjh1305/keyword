package com.example.demo.api.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportPeriodRepository extends JpaRepository<ReportPeriod, Long> {

    @Query("SELECT p FROM ReportPeriod p WHERE p.status = 'OPEN' ORDER BY p.startDate DESC")
    Optional<ReportPeriod> findOpenPeriod();

    List<ReportPeriod> findAllByOrderByStartDateDesc();

    @Query("SELECT p FROM ReportPeriod p WHERE p.status = 'CONFIRMED' AND p.startDate < " +
           "(SELECT cp.startDate FROM ReportPeriod cp WHERE cp.id = :currentPeriodId) " +
           "ORDER BY p.startDate DESC")
    Optional<ReportPeriod> findPreviousPeriod(@Param("currentPeriodId") Long currentPeriodId);
}
