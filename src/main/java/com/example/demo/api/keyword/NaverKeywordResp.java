package com.example.demo.api.keyword;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NaverKeywordResp<T> {
    private T keywordList;
}
