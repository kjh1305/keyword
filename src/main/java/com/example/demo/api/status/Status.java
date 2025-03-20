package com.example.demo.api.status;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@Data
@RedisHash("status")
public class Status {

    @Id
    private final Long id;
    private String filename;//현재 처리중인 파일명

    private Integer filteringTotal;//필터링 작업 총량
    private Integer filteringProgress;//필터링 작업 progress
    private Integer excelTotal;//엑셀 가공 작업 총량
    private Integer excelProgress;//엑셀 가공 작업 progress

    private Integer statusCode; //다운로드중 : 2 , 성공 : 1 , 대기중 : 0 , 엑셀 생성중 : 3 ,,키프리스 초과에러 : -1 , 강제종료 : -9
    private String author;

    private int excelConvertAttempt = 0;//엑셀 변환 실패시 시도 횟수


    // 상태 코드 정의 (상수)
    public static final int STATUS_WAITING = 0;          // 대기 중
    public static final int STATUS_SUCCESS = 1;         // 성공
    public static final int STATUS_DOWNLOADING = 2;     // 다운로드 중
    public static final int STATUS_EXCEL_CREATING = 3;  // 엑셀 생성 중
    public static final int STATUS_ERROR_LIMIT_EXCEEDED = -1; // 키프리스 초과 에러
    public static final int STATUS_FORCE_STOPPED = -9;  // 강제 종료
}
