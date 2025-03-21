package com.example.demo.api.queue.consumer;

import com.example.demo.api.keyword.KeywordService;
import com.example.demo.api.keyword.apicount.ApiCount;
import com.example.demo.api.keyword.apicount.ApiCountService;
import com.example.demo.api.keyword.backup.Backup;
import com.example.demo.api.keyword.backup.BackupService;
import com.example.demo.api.keyword.category.CategoryKeyword;
import com.example.demo.api.keyword.category.CategoryService;
import com.example.demo.api.keyword.work.Work;
import com.example.demo.api.keyword.work.WorkService;
import com.example.demo.api.status.Status;
import com.example.demo.api.status.StatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkMessageListener {

    @Value("${spring.file.upload-dir.input}")
    private String REMOTE_INPUT_FILE_PATH;

    @Value("${spring.file.upload-dir.result}")
    private String REMOTE_RESULT_FILE_PATH;

    private static final int STATUS_CODE_IN_WAIT = 0;
    private static final int STATUS_CODE_IN_SUCCESS = 1;
    private static final int STATUS_CODE_IN_PROGRESS = 2;
    private static final int STATUS_CODE_IN_RUNNING = 3;
    private static final int WORK_CODE_IN_SUCCESS = 1;
    private static final int WORK_CODE_IN_FAIL = 3;
    private static final int WORK_CODE_IN_KILL = -9;
    private static final int VALID_CANDIDATE_COUNT = 4;

    private final BackupService backupService;
    private final KeywordService keywordService;
    private final StatusService statusService;
    private final WorkService workService;
    private final CategoryService categoryService;
    private final ApiCountService apiCountService;


    @RabbitListener(queues = "keyword")
    public void receiveMessage(Message delivery) throws Exception {
        log.info("메세지 수신완료");
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

        JSONObject data = parseJson(message);
        if (data == null) {
            log.error("JSON 데이터 파싱 실패: {}", message);
            return; // JSON 파싱 실패 시 처리 중단
        }
        log.info("Received data: {}", data.toJSONString());

        //Work
        int workId = Integer.parseInt(String.valueOf(data.get("workId")));
        int sellerCountMin = Integer.parseInt(String.valueOf(data.get("sellerCountMin")));
        int sellerCountMax = Integer.parseInt(String.valueOf(data.get("sellerCountMax")));
        int searchCount = Integer.parseInt(String.valueOf(data.get("searchCount")));

        Work work = workService.getWorkById(workId);

        //상태 값이 대기중인 경우만 추출 수행
        if(work.getStatusCode()==STATUS_CODE_IN_WAIT){
            work.setStatusCode(STATUS_CODE_IN_PROGRESS);
            work.setDownloadName("");
            workService.updateWork(work);

            String useKipris = String.valueOf(data.get("useKipris"));
            log.info("work id : " + workId + " , 키프리스 사용여부 : " + useKipris);
            extractValidKeyword(workId, 1, sellerCountMin,sellerCountMax,searchCount,useKipris);
        }
        //FIXME 중간의 오류로 재시도하는 코드들과 분류해야함(아직 불완전)
        /*else if(work.getStatusCode()==2){//프로그램 중반에 큐가 종료된경우 실패처리

            Status status = statusService.getStatusById((long) workId);
            status.setStatusCode(3);//현재까지 진행한 파일을 엑셀변환 작업으로 이동시킴

            String useKipris = String.valueOf(data.get("useKipris"));
            log.info("work id : " + workId + " , 키프리스 사용여부 : " + useKipris);
            extractValidKeyword(workId, 1, sellerCountMin,sellerCountMax,searchCount,useKipris);
        }*/

    }

    //유효 키워드 추출
    public void extractValidKeyword(int workId, int excelIndex
                                    ,int sellerCountMin, int sellerCountMax
                                    ,int searchCount, String useKipris) throws Exception {

        //status,work 가져오기
        Status status = statusService.getStatusById((long) workId);
        Work work = workService.getWorkById(workId);

        String resultCode = "";

        List<Backup> dataList;
        //////////////////////////파일 추출///////////////////////////////////////
        if (status.getStatusCode() != STATUS_CODE_IN_RUNNING) {//엑셀 생성중에 에러난 케이스 필터링용 조건문(바로 엑셀 변환으로 건너뜀)

            try {

                statusService.saveOrUpdateStatus(status);

                //파일명 설정
                String filename = status.getFilename();
                String fileHashCode = work.getFileHashcode();

                //excel -> object
                dataList = keywordService.excelToObject(REMOTE_INPUT_FILE_PATH + fileHashCode+filename, workId);

                //강제종료 시그널
                if(work.getStatusCode() == WORK_CODE_IN_KILL){
                    statusService.deleteStatus(status);
                    log.info("강제종료 시그널");
                    return;
                }

                //status 초기 설정
                status.setStatusCode(STATUS_CODE_IN_PROGRESS);
                status.setFilteringTotal(dataList.size());
                status.setFilteringProgress(excelIndex - 1);
                status.setExcelProgress(0);
                status.setExcelTotal(dataList.size());
                statusService.saveOrUpdateStatus(status);

                //work 초기 설정
                work.setStatusCode(STATUS_CODE_IN_PROGRESS);
                work.setDownloadName("");
                workService.updateWork(work);

                //엑셀 실패시 재시도 횟수 설정
                status.setExcelConvertAttempt(0);

            } catch (Exception e) {
                status.setExcelConvertAttempt(status.getExcelConvertAttempt() + 1);
                statusService.saveOrUpdateStatus(status);
                if (status.getExcelConvertAttempt() == STATUS_CODE_IN_RUNNING) {//종료 시점
                    work.setStatusCode(WORK_CODE_IN_FAIL);
                    work.setEndTime(new Date());
                    workService.updateWork(work);

                    //종료되면 status 삭제
                    Thread.sleep(2000);
                    statusService.deleteStatus(status);
                }
                throw new Exception();
            }

            ////추출 작업
            for (Backup element : dataList) {

                //네이버 일일 호출 횟수량 체크를 위함
                //키워드 추출 작업당 업데이트
                ApiCount apiCountObj = apiCountService.getApiCountById(1);
                int apiCount = apiCountObj.getUseCount();
                log.info("api 사용횟수 >> " + apiCount);
                apiCountObj.setUseCount(apiCount+1);
                apiCountService.updateApiCount(apiCountObj);

                //work, status를 매번 조회하여 종료시그널을 찾아내기 위함
                status = statusService.getStatusById((long)workId);
                work = workService.getWorkById(workId);

                //강제종료 시그널
                if(work.getStatusCode() == WORK_CODE_IN_KILL){
                    statusService.deleteStatus(status);
                    log.info("강제종료 시그널");
                    return;
                }

                try {

                    //상품명과 번호 필수 값이 부족할때 패스
                    if (element.getProduct().equals("필수값부족") &&
                            element.getProductNo().equals("필수값부족")) {
                        log.info("엑셀 필수값 부족");
                        element.setValidKeyword("필수값 부족");
                        element.setKeywordList("필수값 부족");
                        element.setCategory("필수값 부족");
                        backupService.updateBackup(element);
                        continue;
                    }

                    StopWatch stopWatch = new StopWatch();
                    stopWatch.start();

                    //////////// 본격적인 추출 로직
                    //1. 기본 키워드 값 + catId 찾고 저장
                    //에러 발생시 pass
                    String standardKeyword;
                    String wholeCategory;
                    try {
                        wholeCategory = keywordService.findStandardKeyword(element.getProduct());
                        String [] splitWholeCategory = wholeCategory.split(">");
                        standardKeyword = splitWholeCategory[splitWholeCategory.length-1];

                    } catch (Exception e) {
                        log.info(e.toString());
                        log.info("API 비정상 접근");
                        element.setValidKeyword("API 비정상 접근");
                        element.setKeywordList("API 비정상 접근");
                        element.setCategory("API 비정상 접근");
                        backupService.updateBackup(element);
                        continue;
                    }
                    log.info("현재 상품이름은  : " + element.getProduct());
                    log.info("기본 키워드(기준 카테고리) : " + standardKeyword);


                    //// CHECK : '카테고리/카테고리'형태의 카테고리를 포함한 상품의 경우 상품명 자체에 카테고리를 포함하지 않을 가능성이 큼
                    // 카테고리 값이 상품명에 포함되어있는지 체크
                    if(!element.getProduct().contains(standardKeyword)){
                        log.info("카테고리값이 상품명에 포함되어있지 않음");
                        element.setValidKeyword("카테고리값이 상품명에 포함되어있지 않음");
                        element.setKeywordList("카테고리값이 상품명에 포함되어있지 않음");
                        element.setCategory("카테고리값이 상품명에 포함되어있지 않음");
                        backupService.updateBackup(element);
                        continue;
                    }


                    String catId = categoryService.getCategoryEntityByWholeName(wholeCategory).getId();
                    element.setCategory(standardKeyword);
                    backupService.updateBackup(element);

                    log.info("기본 키워드의 cat_id : " + catId);

                    //////// 로직 3번
                    // CategoryKeyword 리스트 추출
                    List<CategoryKeyword> categoryKeywordList = categoryService.getCategoryKeywordListByCategoryId(catId);
                    // 리스트 무작위 섞기
                    Collections.shuffle(categoryKeywordList);

                    // 3. 대표키워드 선정
                    //후보키워드 선정 최대 갯수 산정 변수
                    int validCandidateCount = 0;
                    for(CategoryKeyword categoryKeyword : categoryKeywordList){

                        String relKeyword = categoryKeyword.getKeyword();

                        //강제종료 시그널
                        if(work.getStatusCode() == WORK_CODE_IN_KILL){
                            statusService.deleteStatus(status);
                            log.info("강제종료 시그널");
                            return;
                        }

                        try {
                            if (useKipris.equals("사용")) {//키프리스 사용
                                resultCode = keywordService.isValidKeyword(categoryKeyword,sellerCountMin,sellerCountMax,searchCount);
                            } else {
                                resultCode = keywordService.isValidKeywordWithoutKipris(categoryKeyword,sellerCountMin,sellerCountMax,searchCount);
                            }
                            log.info("resultCode 체크 >>> " + resultCode);
                        } catch (Exception e) {
                            continue;
                        }

                        if (resultCode.equals("사용정지")) {
                            break;
                        } else if (resultCode.equals("적합")) {//유효키워드로 선정
                            element.setKeywordList(element.getKeywordList() +","+relKeyword);
                            log.info("유효키워드는 : " + relKeyword);
                            backupService.updateBackup(element);
                            if(validCandidateCount == VALID_CANDIDATE_COUNT){
                                break;
                            }
                            validCandidateCount++;
                        }
                    }

                    if (resultCode.equals("사용정지")) {//resultCode : 22 인경우 강제 종료(키프리스 사용 제한 초과)
                        break;
                    }

                    //유효키워드가 없는 경우
                    if (element.getKeywordList().isEmpty()) {
                        element.setValidKeyword("유효한 키워드 없음");
                        element.setKeywordList("유효한 키워드 없음");
                        element.setCategory(standardKeyword);
                    }else {
                        String keywords = element.getKeywordList();
                        String [] keywordList = keywords.split(",");
                        double random = Math.random();
                        int randomIndex = (int)(random*keywordList.length);
                        //keywordList의 인덱스 0값이 ""이므로 인덱스 1부터 시작하도록 지정
                        if(randomIndex==0)
                            randomIndex+=1;

                        log.info("유효키워드 리스트 >> " + keywords);

                        element.setValidKeyword(keywordList[randomIndex]);
                        backupService.updateBackup(element);
                    }

                    stopWatch.stop();
                    log.info(" 걸린 시간은  " + stopWatch.getTotalTimeSeconds());
                    log.info("===========================================");

                    backupService.updateBackup(element);
                    //progress 증가
                    status.setFilteringProgress(status.getFilteringProgress() + 1);
                    statusService.saveOrUpdateStatus(status);

                } catch (Exception e) {
                    log.info(e.toString());
                    element.setValidKeyword("오류 발생");
                    element.setKeywordList("오류 발생");
                    backupService.updateBackup(element);
                }

            }
        }

        //엑셀 생성중으로 코드 변환
        status.setStatusCode(STATUS_CODE_IN_RUNNING);
        statusService.saveOrUpdateStatus(status);

        //////////////////////////엑셀 변환///////////////////////////////////////
        //에러 처리 (재시도 3번)
        String downloadName = "";
        try {
            downloadName = keywordService.makeResultExcel("result", status, workId);
        } catch (Exception e) {
            log.info(e.getMessage());
            status.setExcelConvertAttempt(status.getExcelConvertAttempt() + 1);
            statusService.saveOrUpdateStatus(status);
            if (status.getExcelConvertAttempt() == STATUS_CODE_IN_RUNNING) {//종료 시점
                work.setStatusCode(WORK_CODE_IN_FAIL);
                work.setEndTime(new Date());
                workService.updateWork(work);

                //종료되면 status 삭제
                Thread.sleep(2000);
                statusService.deleteStatus(status);
            }
            throw new Exception();
        }

        //////////////////////////최종 작업///////////////////////////////////////
        //status 완료 처리
        status.setStatusCode(STATUS_CODE_IN_SUCCESS);
        statusService.saveOrUpdateStatus(status);

        if (resultCode.equals("사용정지")) {//status코드에 키프리스 api 사용초과 저장
            work.setStatusCode(-1);
        } else {
            if (downloadName.isEmpty())
                work.setStatusCode(WORK_CODE_IN_FAIL);
            work.setStatusCode(WORK_CODE_IN_SUCCESS);
        }

        work.setDownloadName(downloadName);
        work.setEndTime(new Date());
        workService.updateWork(work);

        //종료되면 status 삭제
        Thread.sleep(5000);
        statusService.deleteStatus(status);

        log.info("추출 프로세스 종료");
    }

    private JSONObject parseJson(String message) {
        try {
            JSONParser parser = new JSONParser();
            return (JSONObject) parser.parse(message);
        } catch (Exception e) {
            log.error("JSON 파싱 중 오류 발생", e);
            return null;
        }
    }
}
