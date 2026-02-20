package com.example.demo.api.inventory.excel;

import com.example.demo.api.inventory.stock.InventoryService;
import com.example.demo.api.inventory.stock.ReportPeriodDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelService excelService;
    private final InventoryService inventoryService;

    @GetMapping("/inventory/import")
    public String importPage(Model model) {
        model.addAttribute("menu", "import");
        model.addAttribute("periods", inventoryService.getAllPeriods());
        ReportPeriodDTO currentPeriod = inventoryService.getCurrentPeriod();
        model.addAttribute("currentPeriodId", currentPeriod != null ? currentPeriod.getId() : null);
        return "inventory/import";
    }

    /**
     * 엑셀 파일의 시트 목록 조회
     */
    @PostMapping("/api/inventory/import/sheets")
    @ResponseBody
    public ResponseEntity<?> getSheetList(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "파일을 선택해주세요."));
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "엑셀 파일(.xlsx, .xls)만 업로드 가능합니다."));
            }

            List<Map<String, Object>> sheets = excelService.getSheetList(file);
            return ResponseEntity.ok(Map.of("sheets", sheets));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "시트 목록 조회 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/api/inventory/import/parse")
    @ResponseBody
    public ResponseEntity<?> parseExcel(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "sheetIndex", defaultValue = "0") int sheetIndex) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "파일을 선택해주세요."));
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "엑셀 파일(.xlsx, .xls)만 업로드 가능합니다."));
            }

            List<ExcelImportDTO> data = excelService.parseExcel(file, sheetIndex);
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "count", data.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 파싱 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/api/inventory/import")
    @ResponseBody
    public ResponseEntity<?> importData(@RequestBody ImportRequest request) {
        try {
            ImportResult result;
            if (request.getPeriodId() != null) {
                result = excelService.importDataByPeriod(
                        request.getData(),
                        request.getPeriodId(),
                        request.isUpdateExisting()
                );
            } else {
                result = excelService.importData(
                        request.getData(),
                        request.getYearMonth(),
                        request.isUpdateExisting()
                );
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.getSummary(),
                    "result", result
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Import 실패: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/api/inventory/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String yearMonth,
                                                @RequestParam(required = false) Long periodId) throws IOException {
        byte[] excelData;
        String filename;

        if (periodId != null) {
            excelData = excelService.exportToExcelByPeriod(periodId);
            filename = URLEncoder.encode("재고현황_기간" + periodId + ".xlsx", StandardCharsets.UTF_8);
        } else if (yearMonth == null || yearMonth.isEmpty() || "all".equals(yearMonth)) {
            excelData = excelService.exportAllToExcel();
            filename = URLEncoder.encode("재고현황_전체.xlsx", StandardCharsets.UTF_8);
        } else {
            excelData = excelService.exportToExcel(yearMonth);
            filename = URLEncoder.encode("재고현황_" + yearMonth + ".xlsx", StandardCharsets.UTF_8);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }

    @GetMapping("/api/inventory/export/weekly-report")
    public ResponseEntity<byte[]> exportWeeklyReport(@RequestParam(required = false) String yearMonth,
                                                       @RequestParam(required = false) Long periodId) throws IOException {
        byte[] excelData;
        String filename;

        if (periodId != null) {
            excelData = excelService.exportWeeklyReportByPeriod(periodId);
            filename = URLEncoder.encode("당일보고_기간" + periodId + ".xlsx", StandardCharsets.UTF_8);
        } else {
            excelData = excelService.exportWeeklyReport(yearMonth);
            filename = URLEncoder.encode("당일보고_" + yearMonth + ".xlsx", StandardCharsets.UTF_8);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }

    @GetMapping("/api/inventory/export/weekly-breakdown")
    public ResponseEntity<byte[]> exportWeeklyBreakdown(@RequestParam(required = false) String yearMonth,
                                                          @RequestParam(required = false) Long periodId) throws IOException {
        byte[] excelData;
        String filename;

        if (periodId != null) {
            excelData = excelService.exportWeeklyBreakdownReportByPeriod(periodId);
            filename = URLEncoder.encode("주간보고_기간" + periodId + ".xlsx", StandardCharsets.UTF_8);
        } else {
            excelData = excelService.exportWeeklyBreakdownReport(yearMonth);
            filename = URLEncoder.encode("주간보고_" + yearMonth + ".xlsx", StandardCharsets.UTF_8);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }

    @lombok.Data
    public static class ImportRequest {
        private List<ExcelImportDTO> data;
        private String yearMonth;
        private Long periodId;
        private boolean updateExisting;
    }
}
