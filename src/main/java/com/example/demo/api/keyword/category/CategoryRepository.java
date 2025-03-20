package com.example.demo.api.keyword.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CategoryRepository {

    private final CategoryMapper categoryMapper;

    private final CategoryKeywordMapper categoryKeywordMapper;

    // 카테고리명으로 Category 테이블 조회
    public Category getCategoryByWholeName(String wholeName){
        return categoryMapper.getCategoryByWholeName(wholeName);
    }

    // 카테고리 id로 CategoryKeyword 리스트 조회
    public List<CategoryKeyword> getCategoryKeywordListByCategoryId(String categoryId){
        return categoryKeywordMapper.getCategoryKeywordListByCategoryId(categoryId);
    }

}
