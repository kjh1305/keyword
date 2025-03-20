package com.example.demo.api.keyword.category;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryKeywordMapper {

    // 카테고리 id로 CategoryKeyword 리스트 조회
   List<CategoryKeyword> getCategoryKeywordListByCategoryId(String categoryId);
}
