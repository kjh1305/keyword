package com.example.demo.api.keyword.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // 카테고리명으로 Category 테이블 조회
    public Category getCategoryByWholeName(String wholeName){
        return categoryRepository.getCategoryByWholeName(wholeName);
    }

    // 카테고리 id로 CategoryKeyword 리스트 조회
    public List<CategoryKeyword> getCategoryKeywordListByCategoryId(String categoryId){
        return categoryRepository.getCategoryKeywordListByCategoryId(categoryId);
    }
}
