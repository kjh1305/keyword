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

            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
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

    private Map<String, Integer> parseHeader(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();

        for (Cell cell : headerRow) {
            String value = getCellStringValue(cell).toLowerCase().trim();
            int colIndex = cell.getColumnIndex();

            if (value.contains("제품") || value.contains("품명") || value.contains("product")) {
                columnMap.put("productName", colIndex);
            } else if (value.contains("카테고리") || value.contains("분류") || value.contains("category")) {
                columnMap.put("category", colIndex);
            } else if (value.contains("월초") || value.contains("기초") || value.contains("initial")) {
                columnMap.put("initialStock", colIndex);
            } else if (value.contains("사용") || value.contains("used")) {
                columnMap.put("usedQuantity", colIndex);
            } else if (value.contains("남은") || value.contains("잔여") || value.contains("remaining")) {
                columnMap.put("remainingStock", colIndex);
            } else if (value.contains("유효") || value.contains("expiry") || value.contains("만료")) {
                columnMap.put("expiryDate", colIndex);
            } else if (value.contains("단위") || value.contains("unit")) {
                columnMap.put("unit", colIndex);
            } else if (value.contains("최소") || value.contains("min")) {
                columnMap.put("minQuantity", colIndex);
            } else if (value.contains("비고") || value.contains("note") || value.contains("메모")) {
                columnMap.put("note", colIndex);
            }
        }

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
        if (columnMap.containsKey("expiryDate")) {
            dto.setExpiryDate(getCellDateValue(row.getCell(columnMap.get("expiryDate"))));
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
        if (cell == null) return null;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getLocalDateTimeCellValue().toLocalDate();
                    }
                    return null;
                case STRING:
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) return null;

                    String[] patterns = {"yyyy-MM-dd", "yy.MM.dd", "yyyy.MM.dd", "yy/MM/dd", "yyyy/MM/dd"};
                    for (String pattern : patterns) {
                        try {
                            return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
                        } catch (Exception ignored) {}
                    }
                    return null;
                default:
                    return null;
            }
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
            String latestExpiry = null;

            for (StockOrder order : orders) {
                if (!"COMPLETED".equals(order.getStatus())) continue;
                if (order.getReceivedDate() == null) continue;

                // 해당 월에 입고된 건만 필터
                if (order.getReceivedDate().getYear() == targetYear &&
                    order.getReceivedDate().getMonthValue() == targetMonth) {

                    if (order.getQuantity() != null) {
                        totalQty = totalQty.add(order.getQuantity());
                    }
                    receivedDates.add(order.getReceivedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

                    if (order.getExpiryDate() != null) {
                        latestExpiry = order.getExpiryDate().format(DateTimeFormatter.ofPattern("yy.MM.dd"));
                    }
                }
            }

            productOrderQtyMap.put(productId, totalQty);
            productReceivedDatesMap.put(productId, receivedDates);
            if (latestExpiry != null) {
                productExpiryMap.put(productId, latestExpiry);
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
