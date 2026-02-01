package com.example.demo.api.inventory.excel;

import com.example.demo.api.inventory.order.StockOrder;
import com.example.demo.api.inventory.order.StockOrderRepository;
import com.example.demo.api.inventory.product.Product;
import com.example.demo.api.inventory.product.ProductRepository;
import com.example.demo.api.inventory.stock.Inventory;
import com.example.demo.api.inventory.stock.InventoryDTO;
import com.example.demo.api.inventory.stock.InventoryRepository;
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

    public List<ExcelImportDTO> parseExcel(MultipartFile file) throws IOException {
        List<ExcelImportDTO> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
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
            CellType cellType = cell.getCellType();
            // FORMULA인 경우 캐시된 값 타입 확인
            if (cellType == CellType.FORMULA) {
                cellType = cell.getCachedFormulaResultType();
            }

            switch (cellType) {
                case NUMERIC:
                    // 엑셀 날짜 형식 시도
                    try {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            dates.add(cell.getLocalDateTimeCellValue().toLocalDate());
                        } else {
                            // 날짜 형식이 아니어도 숫자가 날짜 범위면 시도
                            double numValue = cell.getNumericCellValue();
                            if (numValue > 1 && numValue < 100000) {
                                java.util.Date javaDate = DateUtil.getJavaDate(numValue);
                                dates.add(javaDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                            }
                        }
                    } catch (Exception e) {
                        log.debug("날짜 파싱 실패 (NUMERIC): {}", e.getMessage());
                    }
                    break;
                case STRING:
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) return null;

                    // 여러 날짜가 "/" 또는 ","로 구분된 경우 처리
                    String[] dateStrings = value.split("[/,]");

                    for (String dateStr : dateStrings) {
                        LocalDate parsed = parseSingleDate(dateStr.trim());
                        if (parsed != null) {
                            dates.add(parsed);
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.debug("날짜 파싱 실패: {}", e.getMessage());
            return null;
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

        return null;
    }

    /**
     * 주문수량 파싱 - 특수 형식 지원
     * 예: "3+1" -> 4, "1box(20개입)" -> 20, "10" -> 10
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

            // 2. "1box(20개입)" 형식 처리 - 괄호 안 숫자 추출
            if (value.contains("(") && value.contains(")")) {
                int start = value.indexOf("(");
                int end = value.indexOf(")");
                if (start < end) {
                    String insideParens = value.substring(start + 1, end);
                    String numStr = insideParens.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        return new BigDecimal(numStr);
                    }
                }
            }

            // 3. 일반 숫자 추출
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

                if (yearMonth != null && !yearMonth.isEmpty()) {
                    Inventory inventory = inventoryRepository.findByProductIdAndYearMonth(product.getId(), yearMonth)
                            .orElse(Inventory.builder()
                                    .product(product)
                                    .yearMonth(yearMonth)
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

                    inventoryRepository.save(inventory);
                    result.setInventoryRecords(result.getInventoryRecords() + 1);
                }

                // 입고일/유효기간이 있으면 StockOrder 생성 (입고 완료 상태로)
                List<LocalDate> receivedDates = dto.getReceivedDates();
                List<LocalDate> expiryDates = dto.getExpiryDates();

                // 주문수량 결정: orderQuantity가 있으면 사용, 없으면 initialStock 사용
                BigDecimal quantity = dto.getOrderQuantity() != null ? dto.getOrderQuantity() : dto.getInitialStock();
                // 주문수량 원본 저장 (비고에 추가)
                String orderNote = dto.getNote();
                if (dto.getOrderQuantityRaw() != null && !dto.getOrderQuantityRaw().isEmpty()
                        && dto.getOrderQuantity() != null) {
                    // 원본과 파싱된 값이 다르면 원본도 기록
                    String rawNum = dto.getOrderQuantityRaw().replaceAll("[^0-9]", "");
                    if (!rawNum.equals(dto.getOrderQuantity().stripTrailingZeros().toPlainString())) {
                        orderNote = (orderNote != null ? orderNote + " / " : "") + "주문: " + dto.getOrderQuantityRaw();
                    }
                }

                if (receivedDates != null && !receivedDates.isEmpty()) {
                    // 가장 빠른 입고일 찾기
                    LocalDate earliestDate = receivedDates.stream().min(LocalDate::compareTo).orElse(null);

                    // 입고일 개수만큼 StockOrder 생성 (가장 빠른 날짜에만 수량, 나머지는 0)
                    for (int i = 0; i < receivedDates.size(); i++) {
                        LocalDate receivedDate = receivedDates.get(i);
                        // 유효기간은 같은 인덱스의 값 또는 마지막 값 사용
                        LocalDate expiryDate = null;
                        if (expiryDates != null && !expiryDates.isEmpty()) {
                            expiryDate = (i < expiryDates.size()) ? expiryDates.get(i) : expiryDates.get(expiryDates.size() - 1);
                        }

                        // 가장 빠른 날짜에만 수량 저장, 나머지는 0
                        BigDecimal orderQty = receivedDate.equals(earliestDate) ? quantity : BigDecimal.ZERO;

                        StockOrder order = StockOrder.builder()
                                .product(product)
                                .quantity(orderQty)
                                .orderQuantity(dto.getOrderQuantityRaw())
                                .orderDate(receivedDate)
                                .receivedDate(receivedDate)
                                .expiryDate(expiryDate)
                                .status("COMPLETED")
                                .note(orderNote)
                                .build();
                        stockOrderRepository.save(order);
                    }
                } else if (expiryDates != null && !expiryDates.isEmpty()) {
                    // 입고일은 없고 유효기간만 있는 경우 - 가장 빠른 유효기간에만 수량
                    LocalDate earliestExpiry = expiryDates.stream().min(LocalDate::compareTo).orElse(null);

                    for (LocalDate expiryDate : expiryDates) {
                        BigDecimal orderQty = expiryDate.equals(earliestExpiry) ? quantity : BigDecimal.ZERO;

                        StockOrder order = StockOrder.builder()
                                .product(product)
                                .quantity(orderQty)
                                .orderQuantity(dto.getOrderQuantityRaw())
                                .expiryDate(expiryDate)
                                .status("COMPLETED")
                                .note(orderNote)
                                .build();
                        stockOrderRepository.save(order);
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

            // 단일 시트 생성
            createSheetForYearMonth(workbook, yearMonth, styles);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportAllToExcel() throws IOException {
        List<String> yearMonths = inventoryRepository.findAllYearMonths();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 스타일 생성 (한 번만)
            Map<String, CellStyle> styles = createStyles(workbook);

            // 각 년월별로 시트 생성
            for (String yearMonth : yearMonths) {
                createSheetForYearMonth(workbook, yearMonth, styles);
            }

            workbook.write(out);
            return out.toByteArray();
        }
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
        List<Inventory> inventories = inventoryRepository.findByYearMonthWithProduct(yearMonth);

        // 해당 월 기준 연/월 파싱
        int targetYear = Integer.parseInt(yearMonth.substring(0, 4));
        int targetMonth = Integer.parseInt(yearMonth.substring(5, 7));

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
                if (!"COMPLETED".equals(order.getStatus())) continue;

                // 입고일이 있는 경우 해당 월 필터
                if (order.getReceivedDate() != null) {
                    if (order.getReceivedDate().getYear() == targetYear &&
                        order.getReceivedDate().getMonthValue() == targetMonth) {

                        if (order.getQuantity() != null) {
                            totalQty = totalQty.add(order.getQuantity());
                        }
                        receivedDates.add(order.getReceivedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

                        if (order.getExpiryDate() != null) {
                            expiryDates.add(order.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")));
                        }
                    }
                } else if (order.getExpiryDate() != null) {
                    // 입고일 없이 유효기간만 있는 경우도 추가
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

        Sheet sheet = workbook.createSheet(yearMonth);

        // 연/월 추출 (예: 2026-01 -> 2026년 1월)
        String year = yearMonth.substring(0, 4);
        String month = yearMonth.substring(5);
        if (month.startsWith("0")) month = month.substring(1);

        // 타이틀 행
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(year + "년 " + month + "월 피부 물품(보고용)");
        titleCell.setCellStyle(styles.get("title"));

        // 타이틀 셀 병합
        String[] headers = {"번호", "카테고리", "제품명", "월초재고", "추가재고", "사용량", "남은재고", "주문수량", "입고일자", "유효기간", "단위", "비고"};
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        // 헤더 행
        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header"));
        }

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

            // 번호
            Cell cell0 = row.createCell(0);
            cell0.setCellValue(seq);
            cell0.setCellStyle(styles.get("center"));

            // 카테고리
            Cell cell1 = row.createCell(1);
            cell1.setCellValue(inv.getProduct().getCategory() != null ? inv.getProduct().getCategory() : "");
            cell1.setCellStyle(styles.get("center"));

            // 제품명
            Cell cell2 = row.createCell(2);
            cell2.setCellValue(inv.getProduct().getName());
            cell2.setCellStyle(styles.get("text"));

            // 월초재고
            Cell cell3 = row.createCell(3);
            cell3.setCellValue(inv.getInitialStock() != null ? inv.getInitialStock().intValue() : 0);
            cell3.setCellStyle(styles.get("number"));

            // 추가재고
            Cell cell4 = row.createCell(4);
            cell4.setCellValue(inv.getAddedStock() != null ? inv.getAddedStock().intValue() : 0);
            cell4.setCellStyle(styles.get("number"));

            // 사용량
            Cell cell5 = row.createCell(5);
            cell5.setCellValue(inv.getUsedQuantity() != null ? inv.getUsedQuantity().intValue() : 0);
            cell5.setCellStyle(styles.get("number"));

            // 남은재고 (강조)
            Cell cell6 = row.createCell(6);
            cell6.setCellValue(inv.getRemainingStock() != null ? inv.getRemainingStock().intValue() : 0);
            cell6.setCellStyle(styles.get("highlight"));

            // 주문수량 (해당 월 합산)
            Cell cell7 = row.createCell(7);
            BigDecimal totalQty = productOrderQtyMap.get(productId);
            cell7.setCellValue(totalQty != null && totalQty.compareTo(BigDecimal.ZERO) > 0 ? totalQty.intValue() : 0);
            cell7.setCellStyle(styles.get("number"));

            // 입고일자 (줄바꿈으로 구분)
            Cell cell8 = row.createCell(8);
            cell8.setCellValue(dates != null && !dates.isEmpty() ? String.join("\n", dates) : "");
            cell8.setCellStyle(styles.get("wrap"));

            // 유효기간
            Cell cell9 = row.createCell(9);
            String expiry = productExpiryMap.get(productId);
            cell9.setCellValue(expiry != null ? expiry : "");
            cell9.setCellStyle(styles.get("center"));

            // 단위
            Cell cell10 = row.createCell(10);
            cell10.setCellValue(inv.getProduct().getUnit() != null ? inv.getProduct().getUnit() : "");
            cell10.setCellStyle(styles.get("center"));

            // 비고
            Cell cell11 = row.createCell(11);
            cell11.setCellValue(inv.getNote() != null ? inv.getNote() : "");
            cell11.setCellStyle(styles.get("text"));

            rowNum++;
            seq++;
        }

        // 컬럼 너비 설정
        sheet.setColumnWidth(0, 2000);   // 번호
        sheet.setColumnWidth(1, 3500);   // 카테고리
        sheet.setColumnWidth(2, 7000);   // 제품명
        sheet.setColumnWidth(3, 3000);   // 월초재고
        sheet.setColumnWidth(4, 3000);   // 추가재고
        sheet.setColumnWidth(5, 3000);   // 사용량
        sheet.setColumnWidth(6, 3000);   // 남은재고
        sheet.setColumnWidth(7, 3000);   // 주문수량
        sheet.setColumnWidth(8, 4000);   // 입고일자
        sheet.setColumnWidth(9, 3500);   // 유효기간
        sheet.setColumnWidth(10, 2500);  // 단위
        sheet.setColumnWidth(11, 5000);  // 비고
    }
}
