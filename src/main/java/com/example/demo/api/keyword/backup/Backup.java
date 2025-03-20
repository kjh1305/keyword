package com.example.demo.api.keyword.backup;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;

import java.util.Date;

/**
 * 유효키워드 추출 결과값 저장
 */
@ToString
@Getter
@Setter
public class Backup {

    @Id
    private Integer id;
    private String product;
    private String productNo;
    private String validKeyword;
    private Integer statusCode;
    private Integer workId;
    private Integer excelIndex;
    private String category;
    private String keywordList;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date updatedAt;


}
