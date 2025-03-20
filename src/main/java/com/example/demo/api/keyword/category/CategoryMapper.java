package com.example.demo.api.keyword.category;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper {

    // 카테고리명으로 Category 테이블 조회
    Category getCategoryByWholeName(String wholeName);

}
