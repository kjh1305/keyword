package com.example.demo.api.keyword;

import com.example.demo.api.keyword.work.Work;
import com.example.demo.api.keyword.work.WorkService;
import com.example.demo.api.queue.producer.ProduceService;
import com.example.demo.api.status.Status;
import com.example.demo.api.status.StatusService;
import com.example.demo.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/*
 * 유효 키워드 추출 서비스
 */
@Slf4j
@RestController
@RequestMapping("/api/keyword")
@RequiredArgsConstructor
public class KeywordController {

    @Value("${spring.file.upload-dir.input}")
    private String REMOTE_INPUT_FILE_PATH;

    @Value("${spring.file.upload-dir.result}")
    private String REMOTE_RESULT_FILE_PATH;

    private static final int WORK_CODE_IN_WAIT = 0;

    private final StatusService statusService;

    private final WorkService workService;

    private final ProduceService produceService;


    //파일 작업 큐에 파일 전송
    @PostMapping ("/excel")
    public ResponseEntity<Long> sendExtractWorkToQueue(@RequestParam("file")MultipartFile file
                                       ,@RequestParam("sellerMax")int sellerCountMax
                                       ,@RequestParam("sellerMin")int sellerCountMin
                                       ,@RequestParam("searchCount")int searchCount ,@RequestParam("useKipris") String useKipris) throws Exception{

        log.info("최초로 파일을 수신 받은 부분");


        //판매자수 최소값이 최대값보다 크거나 같은경우 => 0
        if(sellerCountMin>=sellerCountMax)
            sellerCountMin = 0;

        //판매자수 최대값이 0보다 작거나 같은 경우 => 5000
        if(sellerCountMax<=0)
            sellerCountMax = 5000;

        //검색량이 0보다 작거나 같은 경우 => 1000
        if(searchCount <= 0)
            searchCount = 1000;

        Random random = new Random();
        String filename = file.getOriginalFilename();
        String fileHashcode = DateUtil.getCurrentDate()+"__"+ random.nextInt(1000);

        //엑셀파일 저장
        String excelExtension = FilenameUtils.getExtension(filename);
        if (excelExtension == null){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (!excelExtension.equals("xlsx") && !excelExtension.equals("xls") && !excelExtension.equals("xlsm")) {
            throw new IOException("엑셀파일만 업로드 해주세요.");
        }

        Workbook workbook;
        if (excelExtension.equals("xlsx")||excelExtension.equals("xlsm"))
            workbook = new XSSFWorkbook(file.getInputStream());
        else
            workbook = new HSSFWorkbook(file.getInputStream());

        File excelFile = new File(REMOTE_INPUT_FILE_PATH+fileHashcode+filename);
        try {
            FileOutputStream fos = new FileOutputStream(excelFile);
            workbook.write(fos);
            workbook.close();
            fos.close();
        }catch (Exception e){
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        log.info("수신 받은 엑셀파일을 올린 부분 ");

        Work work = new Work();
        work.setFilename(filename);
        work.setFileHashcode(fileHashcode);//파일명 중복방지를 위한 hashcode
        work.setDownloadName("");
        work.setStatusCode(WORK_CODE_IN_WAIT);

        workService.insertWork(work);

        Status status = new Status((long)work.getId());
        status.setStatusCode(Status.STATUS_WAITING);
        status.setFilename(filename);

        Long statusId = statusService.saveOrUpdateStatus(status);

        //큐에 전송
        produceService.sendExtractWorkToQueue(work.getId(),sellerCountMin,sellerCountMax,searchCount,useKipris);

        return ResponseEntity.ok(statusId);
    }

    //파일 다운로드 
    //TODO file path 처리 (유저)
    @GetMapping("/file")
    public ResponseEntity<Void> getExcelFile(HttpServletResponse response, @RequestParam("filename")String filename){
        try {
            Path filePath = Paths.get(REMOTE_RESULT_FILE_PATH).resolve(filename).normalize();

            // 경로가 REMOTE_RESULT_FILE_PATH 내부인지 확인
            if (!filePath.startsWith(Paths.get(REMOTE_RESULT_FILE_PATH))) {
                log.warn("Unauthorized file access attempt: {}", filePath);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            File file = filePath.toFile();

            // 파일 존재 여부 확인
            if (!file.exists() || !file.isFile()) {
                log.warn("File not found: {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // MIME 타입 설정
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) {
                mimeType = "application/octet-stream"; // 기본 MIME 타입
            }

            response.setContentType(mimeType);
            response.setHeader("Content-Transfer-Encoding", "binary;");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            // 파일 읽기 및 전송 (try-with-resources 사용)
            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {

                byte[] buffer = new byte[512];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
                os.flush();
            }

            log.info("File successfully downloaded: {}", filePath);

            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Error while processing file download: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
