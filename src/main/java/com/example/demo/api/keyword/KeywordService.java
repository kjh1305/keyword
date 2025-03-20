package com.example.demo.api.keyword;


import com.example.demo.api.keyword.apicount.ApiCountService;
import com.example.demo.api.keyword.backup.Backup;
import com.example.demo.api.keyword.backup.BackupService;
import com.example.demo.api.keyword.category.CategoryKeyword;
import com.example.demo.api.keyword.work.Work;
import com.example.demo.api.status.Status;
import com.example.demo.api.status.StatusService;
import com.example.demo.common.util.ApiDataUtil;
import com.example.demo.common.util.DateUtil;
import com.example.demo.common.util.Signatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeywordService {

    private final StatusService statusService;

    private final BackupService backupService;

    private final ApiCountService apiCountService;

    //키프리스 api
    @Value("${open-api.kipris.access-key}")
    private String ACCESS_KEY;//키프리스 api key

    //네이버 쇼핑 api
    @Value("${open-api.naver.search-shop.client-id}")
    private String SHOP_CLIENT_ID;
    @Value("${open-api.naver.search-shop.client-secret}")
    private String SHOP_CLIENT_SECRET;

    @Value("${open-api.naver.search-shop.client-id2}")
    private String SHOP_CLIENT_ID2;
    @Value("${open-api.naver.search-shop.client-secret2}")
    private String SHOP_CLIENT_SECRET2;


    //네이버 쇼핑 api
    @Value("${open-api.naver.search-ad.customer-id}")
    private String AD_CUSTOMER_ID;
    @Value("${open-api.naver.search-ad.api-key}")
    private String AD_API_KEY;
    @Value("${open-api.naver.search-ad.secret-key}")
    private String AD_SECRET_KEY;

    @Value("${keyword-service.filter.crawling.interval}")
    private int CRAWLING_INTERVAL;

    @Value("${spring.file.upload-dir.input}")
    private String REMOTE_INPUT_FILE_PATH;

    @Value("${spring.file.upload-dir.result}")
    private String REMOTE_RESULT_FILE_PATH;

    //기본 키워드 찾기(api)
    public String findStandardKeyword(String productName) throws Exception{

        Thread.sleep(150);
        productName = URLEncoder.encode(productName, "UTF-8");

        String apiUrl = "https://openapi.naver.com/v1/search/shop?display=" + 10 + "&query=" + productName;
        URL url = new URL(apiUrl);
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod("GET");

        if(apiCountService.getApiCountById(1).getUseCount() < 25000){
            httpURLConnection.setRequestProperty("X-Naver-Client-Id", SHOP_CLIENT_ID);
            httpURLConnection.setRequestProperty("X-Naver-Client-Secret", SHOP_CLIENT_SECRET);
        }else{
            httpURLConnection.setRequestProperty("X-Naver-Client-Id", SHOP_CLIENT_ID2);
            httpURLConnection.setRequestProperty("X-Naver-Client-Secret", SHOP_CLIENT_SECRET2);
        }

        String resultLine = ApiDataUtil.getApiData(httpURLConnection);

        log.info("resultLine: >>>>>>>>>>>>>>>>"  + resultLine);

        JSONParser parser = new JSONParser();
        JSONObject result = (JSONObject) parser.parse(resultLine);
        //if((JSONArray) result.get("errorCdoe"))

        JSONArray items = (JSONArray) result.get("items");



        ObjectMapper mapper = new ObjectMapper();
        List<NaverShopData> resultList = null;

        // items가 null일 경우 예외처리
        try{
            resultList = mapper.readValue(items.toString(), new TypeReference<List<NaverShopData>>() {});
        }catch (IOException e){
            return null;
        }

        if(resultList.get(0).getCategory1().equals("")){
            //대분류 카테고리가 없는 경우는 오류 상황으로 처리
            throw new Exception();
        }

        String category = resultList.get(0).getCategory1() + ">";


        if(!resultList.get(0).getCategory2().equals("")){
            category += (resultList.get(0).getCategory2() + ">");
        }
        if(!resultList.get(0).getCategory3().equals("")){
            category += (resultList.get(0).getCategory3() + ">");
        }
        if(!resultList.get(0).getCategory4().equals("")){
            category += (resultList.get(0).getCategory4() + ">");
        }

        category = category.substring(0,category.length()-1);

        return category;
    }

    //기본 키워드 + catId 찾기(크롤링)
    public HashMap<String, String> findStandardKeyword(String productName, int recursiveCount) throws Exception {

        //throw 테스트
        if (recursiveCount == 10) {
            throw new Exception();
        }

        String url = String.format(
                "https://shopping.naver.com/search/all?query=%s",
                URLEncoder.encode(productName, "UTF-8"));

        org.jsoup.nodes.Document document = Jsoup.connect(url)
                .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Safari/537.36")
                .get();

        Thread.sleep(CRAWLING_INTERVAL);

        //비정상접근 체크
        Elements checkInvalidConnection = document.select(".style_head__2HGCm");
        if (checkInvalidConnection.size() != 0) {

            Thread.sleep(3000);
            log.info("기본 카테고리를 찾는 중 비정상접근 입니다.");
            return findStandardKeyword(productName, recursiveCount + 1);
        }

        Elements adElements = document.select(".ad_ad_stk__12U34");

        int categoryIndex;
        if (adElements.size() == 0) {
            //광고 x
            categoryIndex = 0;
        } else {
            //광고 o
            categoryIndex = 3;
        }

        Elements elements = document.select(".basicList_depth__2QIie");
        Element element = elements.get(categoryIndex);

        Elements children = element.children();
        Element resultElement = children.get(children.size() - 1);


        String standardKeyword = resultElement.text();

        //공백 제거
        standardKeyword = standardKeyword.replaceAll(" ", "");
        standardKeyword = standardKeyword.replaceAll("\\p{Z}", "");
        standardKeyword = standardKeyword.replaceAll("(^\\p{Z}+|\\p{Z}+$)", "");

        String catIdUrl = resultElement.attr("href");
        String[] splitCatIdUrl = catIdUrl.split("=");

        String catId = splitCatIdUrl[1];

        ///////////////필요한 두가지 값 추출
        HashMap<String, String> resultMap = new HashMap<>();
        resultMap.put("standardKeyword", standardKeyword);
        resultMap.put("catId", catId);

        return resultMap;
    }

    //판매자수 조건 체크(네이버 api)
    public boolean isAppropriateSellerCount(String relKeyword, int sellerCountMin, int sellerCountMax) throws Exception {
        //1초에 검색 10회 제한 딜레이
        Thread.sleep(150);

        relKeyword = URLEncoder.encode(relKeyword, "UTF-8");

        String apiUrl = "https://openapi.naver.com/v1/search/shop?query=" + relKeyword;
        URL url = new URL(apiUrl);
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod("GET");
        httpURLConnection.setRequestProperty("X-Naver-Client-Id", "JMsIHUXjaJ09gFblq07w");
        httpURLConnection.setRequestProperty("X-Naver-Client-Secret", "htyaWCZ_g0");

        String resultLine = ApiDataUtil.getApiData(httpURLConnection);

        JSONParser jsonParser = new JSONParser();
        JSONObject result = (JSONObject) jsonParser.parse(resultLine);
        Long totalValue = (Long) result.get("total");

        return totalValue > sellerCountMin && totalValue <= sellerCountMax;
    }

    //네이버 검색순위 가져오기
    //TODO start,enddate 어떻게할지 결정해야함
    //TODO 비정상접근 떠서 에러처리나는 경우 예외처리해야함
    public List<String> getkeywordRank(String cid) throws Exception {
        String url = "https://datalab.naver.com/shoppingInsight/getCategoryKeywordRank.naver";
        List<String> resultList = new ArrayList<>();

        Map<String, String> map = new HashMap<>();
        map.put("cid", cid);
        map.put("timeUnit", "date");
        map.put("startDate", "2021-07-17");
        map.put("endDate", "2021-08-17");
        map.put("count", "20");
        map.put("age", "");
        map.put("gender", "");
        map.put("device", "");

        for (int i = 1; i <= 25; i++) {

            Thread.sleep(CRAWLING_INTERVAL);

            map.put("page", String.valueOf(i));

            org.jsoup.nodes.Document document = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Safari/537.36")
                    .header("Referer", "https://datalab.naver.com/shoppingInsight/sCategory.naver")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .data(map)
                    .post();

            //TODO 비정상접근 떠서 에러처리나는 경우 예외처리해야함
            String resultJsonStr = document.body().text();
            JSONParser parser = new JSONParser();

            //json 파싱
            JSONArray jsonArray = (JSONArray) ((JSONObject) parser.parse(resultJsonStr)).get("ranks");

            for (Object obj : jsonArray) {
                JSONObject jsonObject = (JSONObject) obj;
                resultList.add((String) jsonObject.get("keyword"));
            }
            map.remove("page");
        }

        return resultList;
    }

    //적합 키워드 체크 키프리스 없이(테스트용)
    //적합 키워드 체크
    public String isValidKeywordWithoutKipris(CategoryKeyword categoryKeyword, int sellerCountMin, int sellerCountMax, int adSearchCount) {
        String relKeyword = categoryKeyword.getKeyword();

        try {
            if (this.checkValidValue(categoryKeyword, sellerCountMin, sellerCountMax,adSearchCount)) {
                return "적합";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "부적합";
        }
        return "부적합";
    }

    //적합 키워드 체크
    public String isValidKeyword(CategoryKeyword categoryKeyword, int sellerCountMin, int sellerCountMax, int adSearchCount) {
        String relKeyword = categoryKeyword.getKeyword();

        try {
            log.info("현재 체크중인 relkeyword >>> " + relKeyword);
            org.w3c.dom.Element kiprisResponse = getKiprisResponse(relKeyword);
            String resultCode = kiprisResponse.getElementsByTagName("resultCode").item(0).getTextContent();
            String resultCount = kiprisResponse.getElementsByTagName("TotalSearchCount").item(0).getTextContent();

            if (resultCode.equals("22")) {//키프리스 api 사용초과시 정지
                return "사용정지";
            } else if (resultCount.equals("0")) {//TODO 사용중이지 않은 특허의 totalSearchCount가 0인지 체크해봐야함
                if (this.checkValidValue(categoryKeyword, sellerCountMin, sellerCountMax,adSearchCount)) return "적합";
                else return "부적합";
            } else {
                return "부적합";
            }

        } catch (Exception e) {
            log.info("키프리스 응답 파싱 오류 체크 >>> "+e.getMessage());
            e.printStackTrace();
            return "부적합";
        }
    }

    //유효성 체크(검색량, 판매자수)
    public boolean checkValidValue(CategoryKeyword categoryKeyword,int sellerCountMin,int sellerCountMax,int adSearchCount) throws Exception{
        int sellerCount = categoryKeyword.getSellStoreCount();
        int searchCount = categoryKeyword.getMonthlyMobileQcCnt() + categoryKeyword.getMonthlyPcQcCnt();

        if(sellerCountMin <= sellerCount && sellerCount <= sellerCountMax){
            return searchCount > adSearchCount;
        }else {
            return false;
        }
    }

    //네이버 광고 api 검색수 필터링
    public List<String> getRelKeyword(String standardKeyword, int adSearchCount) throws Exception {

        String baseUrl = "https://api.naver.com";
        String apiUrl = "/keywordstool";
        Long timestamp = DateUtil.getUnixTimestamp();
        String data = timestamp + "." + "GET" + "." + apiUrl;

        String param = String.format("hintKeywords=%s&showDetail=1", URLEncoder.encode(standardKeyword, "UTF-8"));
        URL url = new URL(baseUrl + apiUrl + "?" + param);
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
        httpURLConnection.setRequestProperty("X-Customer", AD_CUSTOMER_ID);
        httpURLConnection.setRequestProperty("X-API-KEY", AD_API_KEY);
        httpURLConnection.setRequestProperty("X-Signature", Signatures.encode(data, AD_SECRET_KEY));
        httpURLConnection.setRequestMethod("GET");

        String resultLine = ApiDataUtil.getApiData(httpURLConnection);
        NaverKeywordResp<List<NaverKeyword>> result;
        ObjectMapper mapper = new ObjectMapper();
        result = mapper.readValue(resultLine, new TypeReference<NaverKeywordResp<List<NaverKeyword>>>() {
        });

        List<NaverKeyword> dataList = result.getKeywordList();
        List<String> keywordList = new ArrayList<>();

        //검색량 필터링
        for (NaverKeyword naverKeyword : dataList) {
            if (naverKeyword.getMonthlyPcQcCnt().equals("< 10"))
                naverKeyword.setMonthlyPcQcCnt("0");
            if (naverKeyword.getMonthlyMobileQcCnt().equals("< 10"))
                naverKeyword.setMonthlyMobileQcCnt("0");

            if ((Integer.parseInt(naverKeyword.getMonthlyMobileQcCnt()) + Integer.parseInt(naverKeyword.getMonthlyPcQcCnt())) >= adSearchCount)
                keywordList.add(naverKeyword.getRelKeyword());
        }

        return keywordList;
    }

    public org.w3c.dom.Element getKiprisResponse(String relKeyword) throws Exception {
        String baseUrl = "http://plus.kipris.or.kr/openapi/rest/trademarkInfoSearchService";

        String apiUrl = "/trademarkNameMatchSearchInfo";
        Long timestamp = DateUtil.getUnixTimestamp();
        String data = timestamp + "." + "GET" + "." + apiUrl;

        String param = String.format("trademarkNameMatch=%s&accessKey=%s&refused=%s&expiration=%s&withdrawal=%s&cancel=%s&abandonment=%s",
                URLEncoder.encode(relKeyword), URLEncoder.encode(ACCESS_KEY), URLEncoder.encode("false"), URLEncoder.encode("false"), URLEncoder.encode("false"), URLEncoder.encode("false"), URLEncoder.encode("false"));
        URL url = new URL(baseUrl + apiUrl + "?" + param);

        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
        httpURLConnection.setRequestMethod("GET");

        String resultLine = ApiDataUtil.getApiData(httpURLConnection);

        log.info("키프리스 응답 xml 데이터 >>>> " + resultLine);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        InputStream is = new ByteArrayInputStream(resultLine.getBytes());
        Document doc = documentBuilder.parse(is);

        return doc.getDocumentElement();
    }

    //엑셀 데이터를 객체로 변환
    public List<Backup> excelToObject(String path, int workId) throws Exception {

        File excelFile = new File(path);

        String excelExtension = FilenameUtils.getExtension(excelFile.getName());
        System.out.println("확장자 : " + excelExtension);

        if (!excelExtension.equals("xlsx") && !excelExtension.equals("xls") && !excelExtension.equals("xlsm")) {
            throw new IOException("엑셀파일만 업로드 해주세요.");
        }

        FileInputStream inputStream = new FileInputStream(path);

        Workbook workbook;
        int workSheetLength;

        if (excelExtension.equals("xlsx") || excelExtension.equals("xlsm")) {
            workbook = new XSSFWorkbook(inputStream);
        } else {
            workbook = new HSSFWorkbook(inputStream);

        }

        Sheet workSheet = workbook.getSheetAt(0);
        workSheetLength = workSheet.getPhysicalNumberOfRows();

        //xls일 경우 최대 열 65535 제한 예외처리
        if (excelExtension.equals("xls") && workSheetLength >= 65535) {
            workSheetLength = 65534;
        }

        List<Backup> backupList = new ArrayList<>();

        int productIndex;
        int productNoIndex;

        //형식에 따른 indexing
        if (workSheet.getRow(0).getPhysicalNumberOfCells() == 2) {
            productNoIndex = 0;
            productIndex = 1;
        } else {
            productNoIndex = 13;
            productIndex = 14;
        }

        //엑셀 데이터 변환 및 리스트로 저장(상품명 , 상품번호)
        for (int i = 1; i < workSheetLength; i++) {

            Row row = workSheet.getRow(i);

            if (row != null) {
                Cell productCell;
                Cell productNoCell;
                String productNo;
                String product;

                try {
                    productCell = row.getCell(productIndex);
                    productNoCell = row.getCell(productNoIndex);

                    //상품번호 변경
                    product = productCell.getStringCellValue();
                    //GS원장과 기존원장 형식 분리
                    if(productNoIndex == 0){
                        //셀 자체를 stringcell로 변환하여 출력
                        productNoCell.setCellType(CellType.STRING);
                        productNo = String.valueOf(productNoCell.getStringCellValue());
                    }else{
                        productNo = String.valueOf(productNoCell.getStringCellValue());
                    }
                    //productNo = productNo.replaceAll("\\.", "");
                    // 원장 번호 수정 방식
                    /*productNo = productNo.substring(0, productNo.length() - 3);
                    productNo = "8" + productNo;*/

                } catch (Exception e) {
                    e.printStackTrace();
                    product = "필수값부족";
                    productNo = "필수값부족";
                }
                log.info("상품명 >>>> " + product);
                log.info("상품번호 >>>> " + productNo);

                //ValidKeyword 및 backup 객체 생성
                Backup backup = new Backup();
                backup.setProduct(product);
                backup.setProductNo(productNo);
                backup.setWorkId(workId);
                backup.setExcelIndex(i);
                backup.setStatusCode(1);
                backup.setValidKeyword("");
                backup.setKeywordList("");
                backup.setCategory("");
                backupService.insertBackup(backup);

                backupList.add(backup);
            }
        }
        return backupList;
    }


    //최종 결과 엑셀 생성
    public String makeResultExcel(String filename, Status status, int workId) throws Exception {

        List<Backup> backupList = backupService.getBackupsByWorkId(workId);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        //////Workbook으로 변환 작업
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("유효키워드 추출결과");

        XSSFRow navRow = sheet.createRow(0);

        //컬럼명 생성
        navRow.createCell(0).setCellValue("상품명");
        navRow.createCell(1).setCellValue("상품번호");
        navRow.createCell(2).setCellValue("카테고리 변경전");
        navRow.createCell(3).setCellValue("카테고리 변경후");
        navRow.createCell(4).setCellValue("대표키워드");
        for(int i=1;i<=5;i++){
            navRow.createCell(i+4).setCellValue("유효키워드"+i);
        }

        for (int r = 0; r < backupList.size(); r++) {
            try {
                XSSFRow row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(backupList.get(r).getProduct());
                row.createCell(1).setCellValue(backupList.get(r).getProductNo());
                row.createCell(2).setCellValue(backupList.get(r).getCategory());
                // 카테고리 '/' 값 처리
                // '카테고리1/카테고리2' 형태에서 '/'를 포함한 카테고리2 부분을 제거하여 카테고리값 재정립
                int removeSlashIndex = backupList.get(r).getCategory().indexOf('/');
                if(removeSlashIndex==-1){
                    row.createCell(3).setCellValue(backupList.get(r).getCategory());
                }else{
                    row.createCell(3).setCellValue(backupList.get(r).getCategory().substring(0,removeSlashIndex));
                }

                row.createCell(4).setCellValue(backupList.get(r).getValidKeyword());

                //유효키워드 리스트 값 엑셀에 생성
                String[] keywordList = backupList.get(r).getKeywordList().split(",");
                int listLength = keywordList.length - 1;
                if (listLength > 5) {
                    listLength = 5;
                }

                for (int i = 1; i <= listLength; i++) {
                    row.createCell(i + 4).setCellValue(keywordList[i]);
                }

                log.info(r + "번째 row 변환 ");

                //status 처리
                status.setExcelProgress(status.getExcelProgress() + 1);
                statusService.saveOrUpdateStatus(status);
            } catch (Exception e) {
                log.info("파일 변환 과정중 에러발생");
                log.info(e.getMessage());
            }

        }

        ////////다운로드 작업
        String downloadName = DateUtil.getCurrentDate() +
                filename + ".xlsx";
        String path = REMOTE_RESULT_FILE_PATH + downloadName;

        File file = new File(path);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            workbook.write(fos);
            workbook.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        stopWatch.stop();
        log.info(String.valueOf(stopWatch.getTotalTimeSeconds()));

        return downloadName;
    }


}
