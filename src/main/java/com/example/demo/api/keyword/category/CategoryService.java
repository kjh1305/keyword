package com.example.demo.api.keyword.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // 카테고리명으로 Category 테이블 조회
    public CategoryDTO getCategoryByWholeName(String wholeName){
        Category category = categoryRepository.getCategoryByWholeName(wholeName);
        if (category == null) {
            return null;
        }
        return CategoryDTO.builder()
                .wholeId(category.getWholeId())
                .parentId(category.getParentId())
                .name(category.getName())
                .wholeName(category.getWholeName())
                .level(category.getLevel())
                .isLast(category.getIsLast())
                .sortOrder(category.getSortOrder())
                .build();
    }

    // 카테고리 id로 CategoryKeyword 리스트 조회
    public List<CategoryKeyword> getCategoryKeywordListByCategoryId(String categoryId){
        return categoryRepository.getCategoryKeywordListByCategoryId(categoryId);
    }
}
