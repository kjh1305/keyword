package com.example.demo.api.keyword;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class NaverKeyword {
    private String relKeyword; //키워드 이름
    private String monthlyPcQcCnt; //Pc 월간 검색수. 10미만일 경우 '< 10'
    private String monthlyMobileQcCnt; //모바일 월간 검색 수. 10미만일 경우 '< 10'
    private String monthlyAvePcClkCnt; //월간 평균 PC 클릭 횟수. 데이터 없는 경우 '0'
    private String monthlyAveMobileClkCnt; //월간 평균 모바일 클릭 횟수. 데이터 없는 경우 '0'
    private String monthlyAvePcCtr; //PC의 클릭율. 데이터 없는 경우 '0'
    private String monthlyAveMobileCtr; //모바일의 클릭율. 데이터 없는 경우 '0'
    private String plAvgDepth; //PC의 평균 깊이. 데이터 없는 경우 '0'
    private String compIdx; //Pc 광고에 기반한 경쟁력 지수

}
