package com.example.demo.api.inventory.excel;

import com.example.demo.api.inventory.order.StockOrder;
import com.example.demo.api.inventory.order.StockOrderRepository;
import com.example.demo.api.inventory.product.Product;
import com.example.demo.api.inventory.product.ProductRepository;
import com.example.demo.api.inventory.stock.Inventory;
import com.example.demo.api.inventory.stock.InventoryDTO;
import com.example.demo.api.inventory.stock.InventoryRepository;
import com.example.demo.api.inventory.stock.UsageLogRepository;
import org.apache.poi.ss.util.CellRangeAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockOrderRepository stockOrderRepository;
    private final UsageLogRepository usageLogRepository;

    /**
     * 엑셀 파일의 시트 목록 조회
     */
    public List<Map<String, Object>> getSheetList(MultipartFile file) throws IOException {
        List<Map<String, Object>> sheets = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            int sheetCount = workbook.getNumberOfSheets();
            log.info("엑셀 파일 시트 개수: {}", sheetCount);

            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Object> sheetInfo = new HashMap<>();
                sheetInfo.put("index", i);
                sheetInfo.put("name", sheet.getSheetName());
                sheetInfo.put("rowCount", sheet.getLastRowNum() + 1);
                sheets.add(sheetInfo);
                log.info("시트 {}: {} ({}행)", i, sheet.getSheetName(), sheet.getLastRowNum() + 1);
            }
        }

        return sheets;
    }

    public List<ExcelImportDTO> parseExcel(MultipartFile file) throws IOException {
        return parseExcel(file, 0); // 기본값: 첫 번째 시트
    }

    public List<ExcelImportDTO> parseExcel(MultipartFile file, int sheetIndex) throws IOException {
        List<ExcelImportDTO> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            if (sheetIndex >= workbook.getNumberOfSheets()) {
                throw new IllegalArgumentException("존재하지 않는 시트입니다: " + sheetIndex);
            }

            Sheet sheet = workbook.getSheetAt(sheetIndex);
            int headerRowIndex = findHeaderRow(sheet);

            if (headerRowIndex < 0) {
                throw new IllegalArgumentException("헤더 행을 찾을 수 없습니다.");
            }

            Map<String, Integer> columnMap = parseHeader(sheet.getRow(headerRowIndex));

            Integer productNameCol = columnMap.get("productName");
            if (productNameCol == null) {
                throw new IllegalArgumentException("제품명 컬럼을 찾을 수 없습니다.");
            }

            // 이중 헤더 여부 확인 - 다음 행이 서브 헤더인지 체크
            int dataStartRow = headerRowIndex + 1;
            if (dataStartRow <= sheet.getLastRowNum()) {
                Row nextRow = sheet.getRow(dataStartRow);
                if (nextRow != null && isSubHeaderRow(nextRow)) {
                    dataStartRow = headerRowIndex + 2; // 이중 헤더면 +2부터 데이터
                }
            }

            for (int i = dataStartRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // 제품명 컬럼 기준으로 빈 행 체크
                Cell productNameCell = row.getCell(productNameCol);
                String productName = getCellStringValue(productNameCell).trim();
                if (productName.isEmpty()) continue;

                try {
                    ExcelImportDTO dto = parseRow(row, columnMap);
                    dto.setProductName(productName); // 이미 trim된 값 사용
                    dto.setRowNumber(i + 1);
                    result.add(dto);
                } catch (Exception e) {
                    log.warn("행 {} 파싱 실패: {}", i + 1, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 서브 헤더 행인지 확인 (이중 헤더 감지)
     */
    private boolean isSubHeaderRow(Row row) {
        for (Cell cell : row) {
            String value = getCellStringValue(cell).toLowerCase().trim();
            // 서브 헤더에 자주 나오는 키워드
            if (value.contains("수량") || value.contains("유효") || value.contains("기간")
                    || value.contains("금액") || value.contains("단가") || value.contains("qty")
                    || value.contains("amount") || value.contains("price")) {
                return true;
            }
        }
        return false;
    }

    private int findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            for (Cell cell : row) {
                String value = getCellStringValue(cell).toLowerCase();
                if (value.contains("제품") || value.contains("품명") || value.contains("product")) {
                    return i;
                }
            }
        }
        return 0;
    }

    /**
     * 이중 헤더 지원 - 메인 헤더와 서브 헤더(다음 행)를 모두 확인
     */
    private Map<String, Integer> parseHeader(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        Sheet sheet = headerRow.getSheet();

        // 메인 헤더 행 파싱
        parseHeaderRow(headerRow, columnMap);

        // 서브 헤더 행 파싱 (다음 행)
        int nextRowIndex = headerRow.getRowNum() + 1;
        if (nextRowIndex <= sheet.getLastRowNum()) {
            Row subHeaderRow = sheet.getRow(nextRowIndex);
            if (subHeaderRow != null) {
                parseHeaderRow(subHeaderRow, columnMap);
            }
        }

        return columnMap;
    }

    private void parseHeaderRow(Row row, Map<String, Integer> columnMap) {
        for (Cell cell : row) {
            String value = getCellStringValue(cell).toLowerCase().trim();
            int colIndex = cell.getColumnIndex();

            // 이미 매핑된 컬럼은 건너뜀 (메인 헤더 우선)
            if (value.isEmpty()) continue;

            if (!columnMap.containsKey("productName") &&
                    (value.contains("제품") || value.contains("품명") || value.contains("product"))) {
                columnMap.put("productName", colIndex);
            } else if (!columnMap.containsKey("category") &&
                    (value.contains("카테고리") || value.contains("분류") || value.contains("category"))) {
                columnMap.put("category", colIndex);
            } else if (!columnMap.containsKey("initialStock") &&
                    (value.contains("월초") || value.contains("기초") || value.contains("initial"))) {
                columnMap.put("initialStock", colIndex);
            } else if (!columnMap.containsKey("usedQuantity") &&
                    (value.contains("사용") || value.contains("used"))) {
                columnMap.put("usedQuantity", colIndex);
            } else if (!columnMap.containsKey("orderQuantity") &&
                    (value.contains("주문") && value.contains("수량") || value.contains("order") && value.contains("qty"))) {
                columnMap.put("orderQuantity", colIndex);
            } else if (!columnMap.containsKey("remainingStock") &&
                    (value.contains("남은") || value.contains("잔여") || value.contains("remaining"))) {
                columnMap.put("remainingStock", colIndex);
            } else if (!columnMap.containsKey("receivedDate") &&
                    (value.contains("입고") || value.contains("received") || value.contains("입하"))) {
                columnMap.put("receivedDate", colIndex);
            } else if (!columnMap.containsKey("expiryDate") &&
                    (value.contains("유효") || value.contains("expiry") || value.contains("만료")
                    || value.contains("exp") || value.contains("사용기한") || value.contains("소비기한"))) {
                columnMap.put("expiryDate", colIndex);
            } else if (!columnMap.containsKey("unit") &&
                    (value.contains("단위") || value.contains("unit"))) {
                columnMap.put("unit", colIndex);
            } else if (!columnMap.containsKey("minQuantity") &&
                    (value.contains("최소") || value.contains("min"))) {
                columnMap.put("minQuantity", colIndex);
            } else if (!columnMap.containsKey("note") &&
                    (value.contains("비고") || value.contains("note") || value.contains("메모"))) {
                columnMap.put("note", colIndex);
            }
        }
    }

    private Map<String, Integer> parseHeaderOld(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        parseHeaderRow(headerRow, columnMap);
        return columnMap;
    }

    private ExcelImportDTO parseRow(Row row, Map<String, Integer> columnMap) {
        ExcelImportDTO dto = new ExcelImportDTO();

        if (columnMap.containsKey("productName")) {
            dto.setProductName(getCellStringValue(row.getCell(columnMap.get("productName"))));
        }
        if (columnMap.containsKey("category")) {
            dto.setCategory(getCellStringValue(row.getCell(columnMap.get("category"))));
        }
        if (columnMap.containsKey("initialStock")) {
            dto.setInitialStock(getCellDecimalValue(row.getCell(columnMap.get("initialStock"))));
        }
        if (columnMap.containsKey("usedQuantity")) {
            dto.setUsedQuantity(getCellDecimalValue(row.getCell(columnMap.get("usedQuantity"))));
        }
        if (columnMap.containsKey("orderQuantity")) {
            Cell orderCell = row.getCell(columnMap.get("orderQuantity"));
            dto.setOrderQuantityRaw(getCellStringValue(orderCell));
            dto.setOrderQuantity(parseOrderQuantity(orderCell));
        }
        if (columnMap.containsKey("receivedDate")) {
            dto.setReceivedDates(getCellDatesValue(row.getCell(columnMap.get("receivedDate"))));
        }
        if (columnMap.containsKey("expiryDate")) {
            dto.setExpiryDates(getCellDatesValue(row.getCell(columnMap.get("expiryDate"))));
        }
        if (columnMap.containsKey("unit")) {
            dto.setUnit(getCellStringValue(row.getCell(columnMap.get("unit"))));
        }
        if (columnMap.containsKey("minQuantity")) {
            BigDecimal minQty = getCellDecimalValue(row.getCell(columnMap.get("minQuantity")));
            dto.setMinQuantity(minQty != null ? minQty.intValue() : null);
        }
        if (columnMap.containsKey("note")) {
            dto.setNote(getCellStringValue(row.getCell(columnMap.get("note"))));
        }

        return dto;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((int) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    private BigDecimal getCellDecimalValue(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) return BigDecimal.ZERO;
                    return new BigDecimal(value.replaceAll("[^0-9.-]", ""));
                case FORMULA:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                default:
                    return BigDecimal.ZERO;
            }
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate getCellDateValue(Cell cell) {
        List<LocalDate> dates = getCellDatesValue(cell);
        if (dates == null || dates.isEmpty()) return null;
        // 가장 빠른 날짜 반환
        return dates.stream().min(LocalDate::compareTo).orElse(null);
    }

    private List<LocalDate> getCellDatesValue(Cell cell) {
        if (cell == null) return null;

        List<LocalDate> dates = new ArrayList<>();

        try {
            String value = getCellStringValue(cell).trim();
            log.info("날짜 셀 원본: [{}]", value);

            if (value.isEmpty()) return null;

            // 모든 종류의 공백을 일반 공백으로 변환
            value = value.replaceAll("[\\s\\u00A0\\u3000]+", " ");

            // "/" 또는 "," 앞뒤 공백 포함해서 분리
            String[] parts = value.split("\\s*/\\s*|\\s*,\\s*");

            log.info("분리 결과: {}개 - {}", parts.length, java.util.Arrays.toString(parts));

            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;

                log.info("파싱 시도: [{}]", part);

                // 점(.)으로 구분된 날짜 형식 처리
                if (part.contains(".")) {
                    String[] nums = part.split("\\.");
                    if (nums.length == 3) {
                        try {
                            int n1 = Integer.parseInt(nums[0].trim());
                            int n2 = Integer.parseInt(nums[1].trim());
                            int n3 = Integer.parseInt(nums[2].trim());

                            int year, month, day;
                            if (n1 > 100) {
                                // yyyy.MM.dd (2026.01.08)
                                year = n1;
                                month = n2;
                                day = n3;
                            } else {
                                // yy.MM.dd (28.06.17)
                                year = (n1 > 50) ? 1900 + n1 : 2000 + n1;
                                month = n2;
                                day = n3;
                            }

                            LocalDate date = LocalDate.of(year, month, day);
                            dates.add(date);
                            log.info("날짜 파싱 성공: {} -> {}", part, date);
                            continue;
                        } catch (Exception e) {
                            log.warn("날짜 변환 실패: {} - {}", part, e.getMessage());
                        }
                    }
                }

                // 다른 형식 시도
                LocalDate parsed = parseSingleDate(part);
                if (parsed != null) {
                    dates.add(parsed);
                    log.info("날짜 파싱 성공(기타): {} -> {}", part, parsed);
                }
            }

            log.info("최종 파싱된 날짜: {}개", dates.size());

        } catch (Exception e) {
            log.error("날짜 파싱 오류: {}", e.getMessage(), e);
        }

        return dates.isEmpty() ? null : dates;
    }

    private LocalDate parseSingleDate(String value) {
        if (value == null || value.isEmpty()) return null;

        // 공백, 특수문자 정리
        value = value.trim().replaceAll("[\\s]+", " ");

        // 다양한 날짜 패턴 지원
        String[] patterns = {
            "yyyy-MM-dd", "yy-MM-dd",
            "yyyy.MM.dd", "yy.MM.dd",
            "yyyy/MM/dd", "yy/MM/dd",
            "yyyy년 MM월 dd일", "yyyy년MM월dd일",
            "MM/dd/yyyy", "MM-dd-yyyy",
            "dd.MM.yy", "dd/MM/yy"
        };

        for (String pattern : patterns) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }

        // 숫자만 추출해서 시도 (예: "281014" -> 28.10.14)
        String numbersOnly = value.replaceAll("[^0-9]", "");
        if (numbersOnly.length() == 6) {
            try {
                int yy = Integer.parseInt(numbersOnly.substring(0, 2));
                int mm = Integer.parseInt(numbersOnly.substring(2, 4));
                int dd = Integer.parseInt(numbersOnly.substring(4, 6));
                int year = (yy > 50) ? 1900 + yy : 2000 + yy;
                return LocalDate.of(year, mm, dd);
            } catch (Exception ignored) {}
        } else if (numbersOnly.length() == 8) {
            try {
                int yyyy = Integer.parseInt(numbersOnly.substring(0, 4));
                int mm = Integer.parseInt(numbersOnly.substring(4, 6));
                int dd = Integer.parseInt(numbersOnly.substring(6, 8));
                return LocalDate.of(yyyy, mm, dd);
            } catch (Exception ignored) {}
        }

        // 마지막으로 구분자로 직접 분리해서 시도
        String[] parts = value.split("[.\\-/]");
        if (parts.length == 3) {
            try {
                int p1 = Integer.parseInt(parts[0].trim());
                int p2 = Integer.parseInt(parts[1].trim());
                int p3 = Integer.parseInt(parts[2].trim());

                // yy.MM.dd 형식 (예: 28.06.17 = 2028-06-17)
                if (p1 >= 20 && p1 <= 99 && p2 >= 1 && p2 <= 12 && p3 >= 1 && p3 <= 31) {
                    return LocalDate.of(2000 + p1, p2, p3);
                }
                // yyyy.MM.dd 형식
                if (p1 >= 2000 && p1 <= 2100 && p2 >= 1 && p2 <= 12 && p3 >= 1 && p3 <= 31) {
                    return LocalDate.of(p1, p2, p3);
                }
                // dd.MM.yy 형식
                if (p1 >= 1 && p1 <= 31 && p2 >= 1 && p2 <= 12 && p3 >= 0 && p3 <= 99) {
                    int year = (p3 > 50) ? 1900 + p3 : 2000 + p3;
                    return LocalDate.of(year, p2, p1);
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * 주문수량 파싱 - 특수 형식 지원
     * 예: "3+1" -> 4, "15 (3 BOX)" -> 15, "10" -> 10
     */
    private BigDecimal parseOrderQuantity(Cell cell) {
        if (cell == null) return null;

        try {
            // 숫자 셀인 경우
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }

            // 문자열인 경우
            String value = getCellStringValue(cell).trim();
            if (value.isEmpty()) return null;

            // 1. "3+1" 형식 처리 (덧셈 계산)
            if (value.contains("+")) {
                String[] parts = value.split("\\+");
                int total = 0;
                for (String part : parts) {
                    String numStr = part.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        total += Integer.parseInt(numStr);
                    }
                }
                if (total > 0) return BigDecimal.valueOf(total);
            }

            // 2. "15 (3 BOX)" 형식 처리 - 괄호 앞 숫자 우선
            if (value.contains("(")) {
                int parenStart = value.indexOf("(");
                String beforeParen = value.substring(0, parenStart).trim();
                String numStr = beforeParen.replaceAll("[^0-9]", "");
                if (!numStr.isEmpty()) {
                    return new BigDecimal(numStr);
                }
            }

            // 3. 일반 숫자 추출 (맨 앞 숫자)
            String numStr = value.replaceAll("[^0-9.]", "");
            if (!numStr.isEmpty() && !numStr.equals(".")) {
                return new BigDecimal(numStr);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Transactional
    public ImportResult importData(List<ExcelImportDTO> data, String yearMonth, boolean updateExisting) {
        ImportResult result = new ImportResult();

        // 선택한 월과 이전 달 계산
        // yearMonth = 선택한 달 (예: 2025-02) → 주문 저장용
        // prevYearMonth = 이전 달 (예: 2025-01) → 재고 데이터 저장용
        String prevYearMonth = null;
        LocalDate orderBaseDate = null; // 주문일 기본값 (선택한 달의 1일)

        if (yearMonth != null && !yearMonth.isEmpty()) {
            java.time.YearMonth currentYM = java.time.YearMonth.parse(yearMonth);
            java.time.YearMonth prevYM = currentYM.minusMonths(1);
            prevYearMonth = prevYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            orderBaseDate = currentYM.atDay(1); // 선택한 달의 1일
            log.info("Import - 선택월: {}, 재고저장월(이전달): {}, 주문기준일: {}", yearMonth, prevYearMonth, orderBaseDate);
        }

        for (ExcelImportDTO dto : data) {
            try {
                Product product = productRepository.findByName(dto.getProductName())
                        .orElse(null);

                if (product == null) {

                    product = Product.builder()
                            .name(dto.getProductName())
                            .category(dto.getCategory())
                            .unit(dto.getUnit() != null ? dto.getUnit() : "개")
                            .minQuantity(dto.getMinQuantity() != null ? dto.getMinQuantity() : 0)
                            .note(dto.getNote())
                            .build();
                    productRepository.save(product);
                    result.setNewProducts(result.getNewProducts() + 1);
                } else if (updateExisting) {
                    if (dto.getCategory() != null) product.setCategory(dto.getCategory());
                    if (dto.getUnit() != null) product.setUnit(dto.getUnit());
                    if (dto.getMinQuantity() != null) product.setMinQuantity(dto.getMinQuantity());
                    if (dto.getNote() != null) product.setNote(dto.getNote());
                    productRepository.save(product);
                    result.setUpdatedProducts(result.getUpdatedProducts() + 1);
                } else {
                    result.setSkippedProducts(result.getSkippedProducts() + 1);
                }

                // 재고 데이터는 이전 달에 저장 (2월 보고서의 재고는 1월 데이터)
                if (prevYearMonth != null) {
                    Inventory inventory = inventoryRepository.findByProductIdAndYearMonth(product.getId(), prevYearMonth)
                            .orElse(Inventory.builder()
                                    .product(product)
                                    .yearMonth(prevYearMonth)
                                    .build());

                    if (dto.getInitialStock() != null) {
                        inventory.setInitialStock(dto.getInitialStock());
                    }
                    if (dto.getUsedQuantity() != null) {
                        inventory.setUsedQuantity(dto.getUsedQuantity());
                    }
                    if (dto.getExpiryDate() != null) {
                        inventory.setExpiryDate(dto.getExpiryDate());
                    }

                    // 남은재고 계산
                    BigDecimal initial = inventory.getInitialStock() != null ? inventory.getInitialStock() : BigDecimal.ZERO;
                    BigDecimal added = inventory.getAddedStock() != null ? inventory.getAddedStock() : BigDecimal.ZERO;
                    BigDecimal used = inventory.getUsedQuantity() != null ? inventory.getUsedQuantity() : BigDecimal.ZERO;
                    inventory.setRemainingStock(initial.add(added).subtract(used));

                    inventoryRepository.save(inventory);
                    result.setInventoryRecords(result.getInventoryRecords() + 1);
                }

                // 주문수량이 있으면 StockOrder 생성 (입고대기 상태로 - 새 주문)
                BigDecimal orderQuantity = dto.getOrderQuantity();
                if (orderQuantity != null && orderQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    // 주문수량 원본 저장 (비고에 추가)
                    String orderNote = dto.getNote();
                    if (dto.getOrderQuantityRaw() != null && !dto.getOrderQuantityRaw().isEmpty()) {
                        String rawNum = dto.getOrderQuantityRaw().replaceAll("[^0-9]", "");
                        if (!rawNum.equals(orderQuantity.stripTrailingZeros().toPlainString())) {
                            orderNote = (orderNote != null ? orderNote + " / " : "") + "주문: " + dto.getOrderQuantityRaw();
                        }
                    }

                    StockOrder order = StockOrder.builder()
                            .product(product)
                            .quantity(orderQuantity)
                            .orderQuantity(dto.getOrderQuantityRaw())
                            .orderDate(orderBaseDate)  // 주문일: 선택한 달의 1일
                            .receivedDate(null)        // 입고일: 미입고
                            .expiryDate(null)          // 유효기간: 미설정 (새 주문이므로)
                            .status("PENDING")         // 입고대기 상태
                            .note(orderNote)
                            .build();
                    stockOrderRepository.save(order);
                    log.info("StockOrder 생성 (입고대기) - 제품: {}, 주문일: {}, 수량: {}",
                            product.getName(), orderBaseDate, orderQuantity);
                }

                // 유효기간이 있으면 기존 재고의 유효기간 추적용 StockOrder 생성
                // (이전 달 재고의 남은 것들 - 입고완료 상태)
                List<LocalDate> expiryDates = dto.getExpiryDates();
                if (expiryDates != null && !expiryDates.isEmpty()) {
                    // 이전 달 기준 주문일 (재고 데이터가 저장되는 달)
                    LocalDate prevMonthDate = orderBaseDate.minusMonths(1).withDayOfMonth(1);

                    for (LocalDate expiryDate : expiryDates) {
                        // 동일 제품, 동일 유효기간의 기존 주문이 있는지 확인
                        boolean exists = stockOrderRepository.findByProductId(product.getId()).stream()
                                .anyMatch(o -> expiryDate.equals(o.getExpiryDate()));

                        if (!exists) {
                            StockOrder expiryOrder = StockOrder.builder()
                                    .product(product)
                                    .quantity(null)            // 수량 미상 (기존 재고)
                                    .remainingQuantity(null)   // 남은 수량 미상
                                    .orderDate(prevMonthDate)  // 이전 달 기준
                                    .receivedDate(prevMonthDate) // 입고일 = 이전 달 1일
                                    .expiryDate(expiryDate)    // 유효기간
                                    .status("COMPLETED")       // 입고완료 상태
                                    .consumed(false)           // 아직 소진 안됨
                                    .note("엑셀 업로드 - 기존 재고 유효기간")
                                    .build();
                            stockOrderRepository.save(expiryOrder);
                            log.info("StockOrder 생성 (유효기간 추적) - 제품: {}, 유효기간: {}",
                                    product.getName(), expiryDate);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("행 {} Import 실패: {}", dto.getRowNumber(), e.getMessage());
                result.getErrors().add("행 " + dto.getRowNumber() + ": " + e.getMessage());
            }
        }

        return result;
    }

    public byte[] exportToExcel(String yearMonth) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 스타일 생성
            Map<String, CellStyle> styles = createStyles(workbook);

            // 단일 시트 생성 (전월 재고 기반, 웹 UI와 동일한 데이터)
            createSheetForYearMonth(workbook, yearMonth, styles);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportAllToExcel() throws IOException {
        // 시트로 표시할 데이터 월 목록 (DB 월 + 현재 월)
        List<String> dbMonths = inventoryRepository.findAllYearMonths();
        String currentMonth = java.time.YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        java.util.Set<String> dataMonths = new java.util.TreeSet<>(dbMonths);

        // 현재 월 미만만 포함 (당월 제외, 오름차순 정렬)
        List<String> sortedDataMonths = new ArrayList<>();
        for (String month : dataMonths) {
            if (month.compareTo(currentMonth) < 0) {
                sortedDataMonths.add(month);
            }
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Map<String, CellStyle> styles = createStyles(workbook);

            // 각 데이터 월에 대해 시트 생성 (해당 월 데이터 직접 조회)
            for (String dataMonth : sortedDataMonths) {
                createSheetForYearMonthDirect(workbook, dataMonth, styles);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 주간 보고용 엑셀 - 간결한 6컬럼 (번호, 제품명, 월초재고, 사용량, 입고완료, 남은재고)
     * 웹 UI(getInventoryByMonthPaged)와 동일한 계산 로직 사용
     */
    public byte[] exportWeeklyReport(String yearMonth) throws IOException {
        // yearMonth = 선택한 달 (예: 2026-02)
        java.time.YearMonth currentYM = java.time.YearMonth.parse(yearMonth);
        java.time.YearMonth prevYM = currentYM.minusMonths(1);
        String prevYearMonth = prevYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int targetYear = currentYM.getYear();
        int targetMonth = currentYM.getMonthValue();

        // 이전 달 재고 데이터 조회 (월초재고 = 이전 달 initialStock)
        List<Inventory> inventories = inventoryRepository.findByYearMonthWithProduct(prevYearMonth);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Map<String, CellStyle> styles = createStyles(workbook);
            Sheet sheet = workbook.createSheet(yearMonth);

            // 타이틀
            String year = yearMonth.substring(0, 4);
            String month = yearMonth.substring(5);
            if (month.startsWith("0")) month = month.substring(1);

            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(30);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(year + "년 " + month + "월 피부 재고현황(당일보고)");
            titleCell.setCellStyle(styles.get("title"));

            String[] headers = {"번호", "제품명", "월초재고", "사용량", "입고완료", "남은재고"};
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

            // 헤더
            Row headerRow = sheet.createRow(1);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styles.get("header"));
            }

            // 데이터 행
            int rowNum = 2;
            int seq = 1;
            for (Inventory inv : inventories) {
                Row row = sheet.createRow(rowNum);
                Long productId = inv.getProduct().getId();

                // 월초재고: 전월초재고 - 전월사용량 = 실제 당월 초 재고
                BigDecimal prevInitial = inv.getInitialStock() != null ? inv.getInitialStock() : BigDecimal.ZERO;
                BigDecimal prevUsed = inv.getUsedQuantity() != null ? inv.getUsedQuantity() : BigDecimal.ZERO;
                BigDecimal initialStock = prevInitial.subtract(prevUsed);

                // 사용량: 당월 운영용(UsageLog 합계)만
                BigDecimal totalUsed = usageLogRepository.sumOperationalUsedByProductIdAndYearMonth(productId, yearMonth);
                if (totalUsed == null) totalUsed = BigDecimal.ZERO;

                // 입고완료: 해당 월 COMPLETED 주문 수량 합계
                BigDecimal completedStock = stockOrderRepository.sumCompletedQuantityByProductIdAndMonth(productId, targetYear, targetMonth);
                if (completedStock == null) completedStock = BigDecimal.ZERO;

                // 남은재고: 월초재고 + 입고완료 - 사용량
                BigDecimal remainingStock = initialStock.add(completedStock).subtract(totalUsed);

                // 번호
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(seq);
                cell0.setCellStyle(styles.get("center"));

                // 제품명
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(inv.getProduct().getName());
                cell1.setCellStyle(styles.get("text"));

                // 월초재고
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(initialStock.intValue());
                cell2.setCellStyle(styles.get("number"));

                // 사용량
                Cell cell3 = row.createCell(3);
                cell3.setCellValue(totalUsed.intValue());
                cell3.setCellStyle(styles.get("number"));

                // 입고완료
                Cell cell4 = row.createCell(4);
                cell4.setCellValue(completedStock.intValue());
                cell4.setCellStyle(styles.get("number"));

                // 남은재고
                Cell cell5 = row.createCell(5);
                cell5.setCellValue(remainingStock.intValue());
                cell5.setCellStyle(styles.get("highlight"));

                rowNum++;
                seq++;
            }

            // 컬럼 너비
            sheet.setColumnWidth(0, 2000);   // 번호
            sheet.setColumnWidth(1, 7000);   // 제품명
            sheet.setColumnWidth(2, 3000);   // 월초재고
            sheet.setColumnWidth(3, 3000);   // 사용량
            sheet.setColumnWidth(4, 3000);   // 입고완료
            sheet.setColumnWidth(5, 3000);   // 남은재고

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 주별 분할 보고서 - 월초재고 1회 + 주마다 사용/입고/남은 컬럼
     * 1주차는 1일부터, 이후 월요일 기준
     */
    public byte[] exportWeeklyBreakdownReport(String yearMonth) throws IOException {
        java.time.YearMonth currentYM = java.time.YearMonth.parse(yearMonth);
        java.time.YearMonth prevYM = currentYM.minusMonths(1);
        String prevYearMonth = prevYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 주 경계 계산: 1주차는 1일~첫 일요일, 이후 월~일
        List<LocalDate[]> weeks = calculateWeekBoundaries(currentYM);

        // 이전 달 재고 데이터 조회
        List<Inventory> inventories = inventoryRepository.findByYearMonthWithProduct(prevYearMonth);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Map<String, CellStyle> styles = createStyles(workbook);
            Sheet sheet = workbook.createSheet(yearMonth);

            String year = yearMonth.substring(0, 4);
            String month = yearMonth.substring(5);
            if (month.startsWith("0")) month = month.substring(1);

            // 타이틀 행
            int totalCols = 3 + (weeks.size() * 3); // 번호 + 제품명 + 월초재고 + (사용/입고/남은) * 주 수
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(30);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(year + "년 " + month + "월 피부 재고현황(주간)");
            titleCell.setCellStyle(styles.get("title"));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));

            // 상단 헤더 행 (주차 그룹)
            Row groupRow = sheet.createRow(1);
            groupRow.setHeightInPoints(20);
            // 번호/제품명/월초재고는 2행 병합
            for (int i = 0; i < 3; i++) {
                Cell cell = groupRow.createCell(i);
                cell.setCellStyle(styles.get("header"));
            }
            // 주차 그룹 헤더
            for (int w = 0; w < weeks.size(); w++) {
                int startCol = 3 + (w * 3);
                LocalDate[] range = weeks.get(w);
                String weekLabel = (w + 1) + "주차 (" + range[0].getMonthValue() + "/" + range[0].getDayOfMonth()
                        + "~" + range[1].getMonthValue() + "/" + range[1].getDayOfMonth() + ")";
                Cell cell = groupRow.createCell(startCol);
                cell.setCellValue(weekLabel);
                cell.setCellStyle(styles.get("header"));
                // 나머지 셀에도 스타일 적용
                groupRow.createCell(startCol + 1).setCellStyle(styles.get("header"));
                groupRow.createCell(startCol + 2).setCellStyle(styles.get("header"));
                sheet.addMergedRegion(new CellRangeAddress(1, 1, startCol, startCol + 2));
            }

            // 하단 헤더 행 (세부 컬럼)
            Row headerRow = sheet.createRow(2);
            headerRow.setHeightInPoints(22);
            String[] fixedHeaders = {"번호", "제품명", "월초재고"};
            for (int i = 0; i < fixedHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(fixedHeaders[i]);
                cell.setCellStyle(styles.get("header"));
            }
            // 번호, 제품명, 월초재고 2행 병합
            for (int i = 0; i < 3; i++) {
                sheet.addMergedRegion(new CellRangeAddress(1, 2, i, i));
            }
            for (int w = 0; w < weeks.size(); w++) {
                int startCol = 3 + (w * 3);
                String[] subHeaders = {"사용", "입고", "남은"};
                for (int s = 0; s < subHeaders.length; s++) {
                    Cell cell = headerRow.createCell(startCol + s);
                    cell.setCellValue(subHeaders[s]);
                    cell.setCellStyle(styles.get("header"));
                }
            }

            // 데이터 행
            int rowNum = 3;
            int seq = 1;
            for (Inventory inv : inventories) {
                Row row = sheet.createRow(rowNum);
                Long productId = inv.getProduct().getId();

                // 월초재고 (실제 당월 초)
                BigDecimal prevInitial = inv.getInitialStock() != null ? inv.getInitialStock() : BigDecimal.ZERO;
                BigDecimal prevUsed = inv.getUsedQuantity() != null ? inv.getUsedQuantity() : BigDecimal.ZERO;
                BigDecimal initialStock = prevInitial.subtract(prevUsed);

                Cell cell0 = row.createCell(0);
                cell0.setCellValue(seq);
                cell0.setCellStyle(styles.get("center"));

                Cell cell1 = row.createCell(1);
                cell1.setCellValue(inv.getProduct().getName());
                cell1.setCellStyle(styles.get("text"));

                Cell cell2 = row.createCell(2);
                cell2.setCellValue(initialStock.intValue());
                cell2.setCellStyle(styles.get("number"));

                // 주별 데이터
                BigDecimal weekStartStock = initialStock;
                for (int w = 0; w < weeks.size(); w++) {
                    LocalDate[] range = weeks.get(w);
                    String startDateStr = range[0].format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    String endDateStr = range[1].format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    int startCol = 3 + (w * 3);

                    // 해당 주 사용량
                    BigDecimal weekUsed = usageLogRepository.sumOperationalUsedByProductIdAndDateRange(productId, startDateStr, endDateStr);
                    if (weekUsed == null) weekUsed = BigDecimal.ZERO;

                    // 해당 주 입고완료
                    BigDecimal weekCompleted = stockOrderRepository.sumCompletedQuantityByProductIdAndDateRange(productId, range[0], range[1]);
                    if (weekCompleted == null) weekCompleted = BigDecimal.ZERO;

                    // 남은재고
                    BigDecimal weekRemaining = weekStartStock.add(weekCompleted).subtract(weekUsed);

                    Cell usedCell = row.createCell(startCol);
                    usedCell.setCellValue(weekUsed.intValue());
                    usedCell.setCellStyle(styles.get("number"));

                    Cell completedCell = row.createCell(startCol + 1);
                    completedCell.setCellValue(weekCompleted.intValue());
                    completedCell.setCellStyle(styles.get("number"));

                    Cell remainingCell = row.createCell(startCol + 2);
                    remainingCell.setCellValue(weekRemaining.intValue());
                    remainingCell.setCellStyle(styles.get("highlight"));

                    // 다음 주 시작재고 = 이번 주 남은재고
                    weekStartStock = weekRemaining;
                }

                rowNum++;
                seq++;
            }

            // 컬럼 너비
            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 7000);
            sheet.setColumnWidth(2, 3000);
            for (int w = 0; w < weeks.size(); w++) {
                int startCol = 3 + (w * 3);
                sheet.setColumnWidth(startCol, 2500);
                sheet.setColumnWidth(startCol + 1, 2500);
                sheet.setColumnWidth(startCol + 2, 2500);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 월의 주 경계 계산: 1주차는 1일~첫 일요일, 이후 월~일, 마지막 주는 월~말일
     */
    private List<LocalDate[]> calculateWeekBoundaries(java.time.YearMonth ym) {
        List<LocalDate[]> weeks = new ArrayList<>();
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();

        LocalDate weekStart = firstDay;

        // 1주차: 1일 ~ 첫 번째 일요일
        // 첫날이 월요일이면 그 주 일요일까지, 그 외는 가장 가까운 일요일까지
        LocalDate firstSunday = firstDay;
        while (firstSunday.getDayOfWeek() != java.time.DayOfWeek.SUNDAY && firstSunday.isBefore(lastDay)) {
            firstSunday = firstSunday.plusDays(1);
        }
        weeks.add(new LocalDate[]{firstDay, firstSunday.isAfter(lastDay) ? lastDay : firstSunday});
        weekStart = firstSunday.plusDays(1); // 다음 월요일

        // 이후 주: 월~일
        while (!weekStart.isAfter(lastDay)) {
            LocalDate weekEnd = weekStart.plusDays(6); // 일요일
            if (weekEnd.isAfter(lastDay)) {
                weekEnd = lastDay;
            }
            weeks.add(new LocalDate[]{weekStart, weekEnd});
            weekStart = weekEnd.plusDays(1);
        }

        return weeks;
    }

    /**
     * DB yearMonth를 직접 사용하여 시트 생성
     */
    private void createSheetForYearMonthDirect(Workbook workbook, String yearMonth, Map<String, CellStyle> styles) {
        // yearMonth가 곧 Inventory의 yearMonth (시트명도 이걸로)
        List<Inventory> inventories = inventoryRepository.findByYearMonthWithProduct(yearMonth);

        // 제품별 주문 정보 조회 (해당 월 기준)
        java.time.YearMonth targetYM = java.time.YearMonth.parse(yearMonth);
        int targetYear = targetYM.getYear();
        int targetMonth = targetYM.getMonthValue();

        Map<Long, BigDecimal> productOrderQtyMap = new HashMap<>();
        Map<Long, List<String>> productReceivedDatesMap = new HashMap<>();
        Map<Long, String> productExpiryMap = new HashMap<>();

        for (Inventory inv : inventories) {
            Long productId = inv.getProduct().getId();
            List<StockOrder> orders = stockOrderRepository.findByProductId(productId);

            BigDecimal totalQty = BigDecimal.ZERO;
            List<String> receivedDates = new ArrayList<>();
            List<String> expiryDates = new ArrayList<>();

            for (StockOrder order : orders) {
                boolean isTargetMonthOrder = order.getOrderDate() != null &&
                        order.getOrderDate().getYear() == targetYear &&
                        order.getOrderDate().getMonthValue() == targetMonth;

                if (isTargetMonthOrder && order.getQuantity() != null) {
                    totalQty = totalQty.add(order.getQuantity());
                }

                if ("COMPLETED".equals(order.getStatus()) && order.getReceivedDate() != null) {
                    if (order.getReceivedDate().getYear() == targetYear &&
                        order.getReceivedDate().getMonthValue() == targetMonth) {
                        receivedDates.add(order.getReceivedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                }

                if ("COMPLETED".equals(order.getStatus()) &&
                    order.getExpiryDate() != null &&
                    (order.getConsumed() == null || !order.getConsumed())) {
                    expiryDates.add(order.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")));
                }
            }

            if (expiryDates.isEmpty() && inv.getExpiryDate() != null) {
                expiryDates.add(inv.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")));
            }

            productOrderQtyMap.put(productId, totalQty);
            productReceivedDatesMap.put(productId, receivedDates);
            if (!expiryDates.isEmpty()) {
                productExpiryMap.put(productId, String.join(" / ", expiryDates));
            }
        }

        // 시트 생성 (yearMonth가 시트명)
        Sheet sheet = workbook.createSheet(yearMonth);

        String year = yearMonth.substring(0, 4);
        String month = yearMonth.substring(5);
        if (month.startsWith("0")) month = month.substring(1);

        // 타이틀 행
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(year + "년 " + month + "월 피부 물품(보고용)");
        titleCell.setCellStyle(styles.get("title"));

        String[] headers = {"번호", "제품명", "월초재고", "사용량", "남은재고", "주문재고", "입고일자", "유효기간", "비고"};
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }

        // 보고 월 문자열 (입고완료, 운영사용량 조회용)
        String reportYearMonth = targetYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int rowNum = 2;
        int seq = 1;
        for (Inventory inv : inventories) {
            Row row = sheet.createRow(rowNum);
            Long productId = inv.getProduct().getId();

            List<String> dates = productReceivedDatesMap.get(productId);
            int dateCount = (dates != null) ? dates.size() : 0;
            if (dateCount > 1) {
                row.setHeightInPoints(dateCount * 15);
            }

            // 남은재고 동적 계산 (웹 UI와 동일한 로직)
            BigDecimal initialStock = inv.getInitialStock() != null ? inv.getInitialStock() : BigDecimal.ZERO;
            BigDecimal reportUsed = inv.getUsedQuantity() != null ? inv.getUsedQuantity() : BigDecimal.ZERO;

            BigDecimal completedStock = stockOrderRepository.sumCompletedQuantityByProductIdAndMonth(productId, targetYear, targetMonth);
            if (completedStock == null) completedStock = BigDecimal.ZERO;

            BigDecimal operationalUsed = usageLogRepository.sumOperationalUsedByProductIdAndYearMonth(productId, reportYearMonth);
            if (operationalUsed == null) operationalUsed = BigDecimal.ZERO;

            BigDecimal remainingStock = initialStock.add(completedStock).subtract(reportUsed).subtract(operationalUsed);

            Cell cell0 = row.createCell(0);
            cell0.setCellValue(seq);
            cell0.setCellStyle(styles.get("center"));

            Cell cell1 = row.createCell(1);
            cell1.setCellValue(inv.getProduct().getName());
            cell1.setCellStyle(styles.get("text"));

            Cell cell2 = row.createCell(2);
            cell2.setCellValue(initialStock.intValue());
            cell2.setCellStyle(styles.get("number"));

            Cell cell3 = row.createCell(3);
            cell3.setCellValue(reportUsed.intValue());
            cell3.setCellStyle(styles.get("number"));

            // 남은재고 (강조) - 동적 계산값 사용
            Cell cell4 = row.createCell(4);
            cell4.setCellValue(remainingStock.intValue());
            cell4.setCellStyle(styles.get("highlight"));

            Cell cell5 = row.createCell(5);
            BigDecimal orderStock = productOrderQtyMap.get(productId);
            cell5.setCellValue(orderStock != null && orderStock.compareTo(BigDecimal.ZERO) > 0 ? orderStock.intValue() : 0);
            cell5.setCellStyle(styles.get("number"));

            Cell cell6 = row.createCell(6);
            cell6.setCellValue(dates != null && !dates.isEmpty() ? String.join("\n", dates) : "");
            cell6.setCellStyle(styles.get("wrap"));

            Cell cell7 = row.createCell(7);
            String expiry = productExpiryMap.get(productId);
            cell7.setCellValue(expiry != null ? expiry : "");
            cell7.setCellStyle(styles.get("center"));

            // 비고 (제품 비고 + 재고 비고)
            Cell cell8 = row.createCell(8);
            String productNote = inv.getProduct().getNote() != null ? inv.getProduct().getNote() : "";
            String inventoryNote = inv.getNote() != null ? inv.getNote() : "";
            String combinedNote = "";
            if (!productNote.isEmpty() && !inventoryNote.isEmpty()) {
                combinedNote = productNote + "\n" + inventoryNote;
            } else if (!productNote.isEmpty()) {
                combinedNote = productNote;
            } else {
                combinedNote = inventoryNote;
            }
            cell8.setCellValue(combinedNote);
            cell8.setCellStyle(styles.get("wrap"));

            rowNum++;
            seq++;
        }

        sheet.setColumnWidth(0, 2000);
        sheet.setColumnWidth(1, 7000);
        sheet.setColumnWidth(2, 3000);
        sheet.setColumnWidth(3, 3000);
        sheet.setColumnWidth(4, 3000);
        sheet.setColumnWidth(5, 3000);
        sheet.setColumnWidth(6, 4000);
        sheet.setColumnWidth(7, 3500);
        sheet.setColumnWidth(8, 5000);
    }

    private Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();

        // 타이틀 스타일
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        styles.put("title", titleStyle);

        // 헤더 스타일
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        styles.put("header", headerStyle);

        // 데이터 스타일 - 기본 (가운데 정렬)
        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.setAlignment(HorizontalAlignment.CENTER);
        centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        centerStyle.setBorderBottom(BorderStyle.THIN);
        centerStyle.setBorderTop(BorderStyle.THIN);
        centerStyle.setBorderLeft(BorderStyle.THIN);
        centerStyle.setBorderRight(BorderStyle.THIN);
        styles.put("center", centerStyle);

        // 데이터 스타일 - 숫자 (오른쪽 정렬)
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setAlignment(HorizontalAlignment.RIGHT);
        numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        numberStyle.setBorderBottom(BorderStyle.THIN);
        numberStyle.setBorderTop(BorderStyle.THIN);
        numberStyle.setBorderLeft(BorderStyle.THIN);
        numberStyle.setBorderRight(BorderStyle.THIN);
        styles.put("number", numberStyle);

        // 데이터 스타일 - 텍스트 (왼쪽 정렬)
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setAlignment(HorizontalAlignment.LEFT);
        textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        textStyle.setBorderBottom(BorderStyle.THIN);
        textStyle.setBorderTop(BorderStyle.THIN);
        textStyle.setBorderLeft(BorderStyle.THIN);
        textStyle.setBorderRight(BorderStyle.THIN);
        styles.put("text", textStyle);

        // 데이터 스타일 - 줄바꿈
        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setAlignment(HorizontalAlignment.CENTER);
        wrapStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        wrapStyle.setWrapText(true);
        wrapStyle.setBorderBottom(BorderStyle.THIN);
        wrapStyle.setBorderTop(BorderStyle.THIN);
        wrapStyle.setBorderLeft(BorderStyle.THIN);
        wrapStyle.setBorderRight(BorderStyle.THIN);
        styles.put("wrap", wrapStyle);

        // 남은재고 강조 스타일
        CellStyle highlightStyle = workbook.createCellStyle();
        highlightStyle.setAlignment(HorizontalAlignment.RIGHT);
        highlightStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        highlightStyle.setBorderBottom(BorderStyle.THIN);
        highlightStyle.setBorderTop(BorderStyle.THIN);
        highlightStyle.setBorderLeft(BorderStyle.THIN);
        highlightStyle.setBorderRight(BorderStyle.THIN);
        highlightStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        highlightStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        highlightStyle.setFont(boldFont);
        styles.put("highlight", highlightStyle);

        return styles;
    }

    private void createSheetForYearMonth(Workbook workbook, String yearMonth, Map<String, CellStyle> styles) {
        // 해당 월 기준 연/월 파싱
        int targetYear = Integer.parseInt(yearMonth.substring(0, 4));
        int targetMonth = Integer.parseInt(yearMonth.substring(5, 7));

        // 이전 달 계산 (재고 데이터용)
        java.time.YearMonth currentYM = java.time.YearMonth.of(targetYear, targetMonth);
        java.time.YearMonth prevYM = currentYM.minusMonths(1);
        String prevYearMonth = prevYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 재고 데이터는 이전 달 기준으로 조회
        List<Inventory> inventories = inventoryRepository.findByYearMonthWithProduct(prevYearMonth);

        // 제품별 해당 월 입고 정보 조회 (합산 수량, 입고일 목록)
        Map<Long, BigDecimal> productOrderQtyMap = new HashMap<>();
        Map<Long, List<String>> productReceivedDatesMap = new HashMap<>();
        Map<Long, String> productExpiryMap = new HashMap<>();

        for (Inventory inv : inventories) {
            Long productId = inv.getProduct().getId();
            List<StockOrder> orders = stockOrderRepository.findByProductId(productId);

            BigDecimal totalQty = BigDecimal.ZERO;
            List<String> receivedDates = new ArrayList<>();
            List<String> expiryDates = new ArrayList<>();

            for (StockOrder order : orders) {
                // 주문일이 해당 월(선택한 월)인 경우
                boolean isTargetMonthOrder = order.getOrderDate() != null &&
                        order.getOrderDate().getYear() == targetYear &&
                        order.getOrderDate().getMonthValue() == targetMonth;

                // 해당 월 주문이면 상태와 관계없이 수량 합산 (PENDING + COMPLETED)
                if (isTargetMonthOrder && order.getQuantity() != null) {
                    totalQty = totalQty.add(order.getQuantity());
                }

                // 입고완료(COMPLETED)인 경우 입고일 추가
                if ("COMPLETED".equals(order.getStatus()) && order.getReceivedDate() != null) {
                    if (order.getReceivedDate().getYear() == targetYear &&
                        order.getReceivedDate().getMonthValue() == targetMonth) {
                        receivedDates.add(order.getReceivedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                }

                // 유효기간은 소진되지 않은 모든 COMPLETED 주문에서 가져옴 (재고의 유효기간)
                if ("COMPLETED".equals(order.getStatus()) &&
                    order.getExpiryDate() != null &&
                    (order.getConsumed() == null || !order.getConsumed())) {
                    expiryDates.add(order.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")));
                }
            }

            // Inventory 자체의 유효기간도 확인
            if (expiryDates.isEmpty() && inv.getExpiryDate() != null) {
                expiryDates.add(inv.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")));
            }

            productOrderQtyMap.put(productId, totalQty);
            productReceivedDatesMap.put(productId, receivedDates);
            // 유효기간 여러 개를 / 로 구분하여 저장
            if (!expiryDates.isEmpty()) {
                productExpiryMap.put(productId, String.join(" / ", expiryDates));
            }
        }

        // 시트명과 타이틀은 데이터 월(이전 달) 기준으로 표시
        Sheet sheet = workbook.createSheet(prevYearMonth);

        // 연/월 추출 (데이터 월 기준)
        String year = prevYearMonth.substring(0, 4);
        String month = prevYearMonth.substring(5);
        if (month.startsWith("0")) month = month.substring(1);

        // 타이틀 행
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(year + "년 " + month + "월 피부 물품(보고용)");
        titleCell.setCellStyle(styles.get("title"));

        // 타이틀 셀 병합
        String[] headers = {"번호", "제품명", "월초재고", "사용량", "남은재고", "주문재고", "입고일자", "유효기간", "비고"};
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        // 헤더 행
        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }

        // yearMonth(보고 월) 기준으로 연/월 값 (입고완료, 운영사용량 조회용)
        String reportYearMonth = currentYM.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int rowNum = 2;
        int seq = 1;
        for (Inventory inv : inventories) {
            Row row = sheet.createRow(rowNum);
            Long productId = inv.getProduct().getId();

            List<String> dates = productReceivedDatesMap.get(productId);
            int dateCount = (dates != null) ? dates.size() : 0;
            if (dateCount > 1) {
                row.setHeightInPoints(dateCount * 15);
            }

            // 남은재고 동적 계산 (웹 UI와 동일한 로직)
            BigDecimal initialStock = inv.getInitialStock() != null ? inv.getInitialStock() : BigDecimal.ZERO;
            BigDecimal reportUsed = inv.getUsedQuantity() != null ? inv.getUsedQuantity() : BigDecimal.ZERO;

            BigDecimal completedStock = stockOrderRepository.sumCompletedQuantityByProductIdAndMonth(productId, targetYear, targetMonth);
            if (completedStock == null) completedStock = BigDecimal.ZERO;

            BigDecimal operationalUsed = usageLogRepository.sumOperationalUsedByProductIdAndYearMonth(productId, reportYearMonth);
            if (operationalUsed == null) operationalUsed = BigDecimal.ZERO;

            BigDecimal remainingStock = initialStock.add(completedStock).subtract(reportUsed).subtract(operationalUsed);

            // 번호
            Cell cell0 = row.createCell(0);
            cell0.setCellValue(seq);
            cell0.setCellStyle(styles.get("center"));

            // 제품명
            Cell cell1 = row.createCell(1);
            cell1.setCellValue(inv.getProduct().getName());
            cell1.setCellStyle(styles.get("text"));

            // 월초재고
            Cell cell2 = row.createCell(2);
            cell2.setCellValue(initialStock.intValue());
            cell2.setCellStyle(styles.get("number"));

            // 사용량
            Cell cell3 = row.createCell(3);
            cell3.setCellValue(reportUsed.intValue());
            cell3.setCellStyle(styles.get("number"));

            // 남은재고 (강조) - 동적 계산값 사용
            Cell cell4 = row.createCell(4);
            cell4.setCellValue(remainingStock.intValue());
            cell4.setCellStyle(styles.get("highlight"));

            // 주문재고 (해당 월 주문수량 합산 - PENDING + COMPLETED)
            Cell cell5 = row.createCell(5);
            BigDecimal orderStock = productOrderQtyMap.get(productId);
            cell5.setCellValue(orderStock != null && orderStock.compareTo(BigDecimal.ZERO) > 0 ? orderStock.intValue() : 0);
            cell5.setCellStyle(styles.get("number"));

            // 입고일자 (줄바꿈으로 구분)
            Cell cell6 = row.createCell(6);
            cell6.setCellValue(dates != null && !dates.isEmpty() ? String.join("\n", dates) : "");
            cell6.setCellStyle(styles.get("wrap"));

            // 유효기간
            Cell cell7 = row.createCell(7);
            String expiry = productExpiryMap.get(productId);
            cell7.setCellValue(expiry != null ? expiry : "");
            cell7.setCellStyle(styles.get("center"));

            // 비고 (제품 비고 + 재고 비고)
            Cell cell8 = row.createCell(8);
            String productNote = inv.getProduct().getNote() != null ? inv.getProduct().getNote() : "";
            String inventoryNote = inv.getNote() != null ? inv.getNote() : "";
            String combinedNote = "";
            if (!productNote.isEmpty() && !inventoryNote.isEmpty()) {
                combinedNote = productNote + "\n" + inventoryNote;
            } else if (!productNote.isEmpty()) {
                combinedNote = productNote;
            } else {
                combinedNote = inventoryNote;
            }
            cell8.setCellValue(combinedNote);
            cell8.setCellStyle(styles.get("wrap"));

            rowNum++;
            seq++;
        }

        // 컬럼 너비 설정
        sheet.setColumnWidth(0, 2000);   // 번호
        sheet.setColumnWidth(1, 7000);   // 제품명
        sheet.setColumnWidth(2, 3000);   // 월초재고
        sheet.setColumnWidth(3, 3000);   // 사용량
        sheet.setColumnWidth(4, 3000);   // 남은재고
        sheet.setColumnWidth(5, 3000);   // 주문재고
        sheet.setColumnWidth(6, 4000);   // 입고일자
        sheet.setColumnWidth(7, 3500);   // 유효기간
        sheet.setColumnWidth(8, 5000);   // 비고
    }
}
