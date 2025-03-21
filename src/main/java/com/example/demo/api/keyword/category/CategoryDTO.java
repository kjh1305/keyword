package com.example.demo.api.keyword.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


@Getter
@AllArgsConstructor
@Builder
public class CategoryDTO {
    private String wholeId;
    private String parentId;
    private String name;//카테고리명(사용)
    private String wholeName;//전체카테고리명
    private int level;
    private int isLast;
    private int sortOrder;
}
