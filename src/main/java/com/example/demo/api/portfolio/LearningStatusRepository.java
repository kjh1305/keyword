package com.example.demo.api.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningStatusRepository extends JpaRepository<LearningStatus, Long> {

    List<LearningStatus> findAllByOrderByAccessedAtDesc();
}
