package com.example.demo.api.keyword.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // 카테고리명으로 Category 테이블 조회
    @GetMapping("/category")
    public ResponseEntity<CategoryDTO> getCategoryByName(@RequestParam("name") String name){
        CategoryDTO categoryDTO = categoryService.getCategoryByWholeName(name);
        if (categoryDTO == null) {
            log.warn(">>>>>>> [getStatusById] No status found for name: {}", name);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(categoryDTO);
    }
}
