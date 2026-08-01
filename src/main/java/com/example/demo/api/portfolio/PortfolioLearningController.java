package com.example.demo.api.portfolio;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PortfolioLearningController {

    private final LearningStatusRepository learningStatusRepository;

    @GetMapping("/api/portfolio/learning")
    public List<LearningStatusResponse> learning() {
        return learningStatusRepository.findAllByOrderByAccessedAtDesc().stream()
                .map(LearningStatusResponse::from)
                .toList();
    }
}
