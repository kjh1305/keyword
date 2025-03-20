package com.example.demo.api.keyword.category;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Data
/**
 * 네이버 항목 카테고리 정보
 */
public class Category {

    private String id;//카테고리 id(사용)
    private String wholeId;
    private String parentId;
    private String name;//카테고리명(사용)
    private String wholeName;//전체카테고리명
    private int level;
    private int isLast;
    private int sortOrder;
    private String createdAt;
    private Date keywordUpdatedAt;
    private Date countUpdatedAt;
    private Date clickUpdatedAt;

}
