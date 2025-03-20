package com.example.demo.api.keyword;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NaverShopData {

    private String title; //상품명. 사용 필드
    private String link;
    private String image; //이미지 url. 사용 필드
    private int lprice;
    private int hprice;
    private String mallName;
    private long productId;
    private int productType;
    private String maker;
    private String brand;
    private String category1; //대카테고리. 사용 필드
    private String category2; //중카테고리. 사용 필드
    private String category3; //소카테고리. 사용 필드
    private String category4; //세카테고리. 사용 필드

}
