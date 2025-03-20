package com.example.demo.api.keyword.work;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
public class Work {

    @Id
    private Integer id;
    private String filename;
    private String fileHashcode;//파일 업로드 했을때 파일명 중복방지를 위한 hashcode

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date startTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private Date endTime;

    private String downloadName;//다운로드 파일명
    private Integer statusCode;//대기중 : 0, 성공 : 1, 진행중 : 2,실패 : 3
    private String author;

}
