package com.example.demo.api.keyword.category;

import lombok.Data;

import java.util.Date;

@Data
/**
 * 네이버 상품 정보
 */
public class CategoryKeyword {

    private int id;
    private String categoryId;//카테고리 id (사용)
    private String keyword;//키워드명 (사용)
    private int rank;

    private String relKeyword;
    private int monthlyPcQcCnt; //Pc 월간 검색수. 10미만일 경우 '< 10' (사용)
    private int monthlyMobileQcCnt; //모바일 월간 검색 수. 10미만일 경우 '< 10' (사용)
    private double monthlyAvePcClkCnt;
    private double monthlyAveMobileClkCnt;
    private double monthlyAvePcCtr;
    private double monthlyAveMobileCtr;
    private int plAvgDepth;
    private String compIdx;
    private int sellStoreCount;//판매자 수 (사용)
    private Date createdAt;
    private Date countUpdatedAt;
    private Date clickUpdatedAt;

}
