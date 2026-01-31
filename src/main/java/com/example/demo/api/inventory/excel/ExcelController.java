package com.example.demo.api.inventory.excel;

import com.example.demo.api.inventory.stock.InventoryService;
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
        model.addAttribute("yearMonths", inventoryService.getAllYearMonths());
        model.addAttribute("currentYearMonth", inventoryService.getCurrentYearMonth());
        return "inventory/import";
    }

    @PostMapping("/api/inventory/import/parse")
    @ResponseBody
    public ResponseEntity<?> parseExcel(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "파일을 선택해주세요."));
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "엑셀 파일(.xlsx, .xls)만 업로드 가능합니다."));
            }

            List<ExcelImportDTO> data = excelService.parseExcel(file);
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
            ImportResult result = excelService.importData(
                    request.getData(),
                    request.getYearMonth(),
                    request.isUpdateExisting()
            );
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
    public ResponseEntity<byte[]> exportExcel(@RequestParam String yearMonth) throws IOException {
        byte[] excelData = excelService.exportToExcel(yearMonth);

        String filename = URLEncoder.encode("재고현황_" + yearMonth + ".xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelData);
    }

    @lombok.Data
    public static class ImportRequest {
        private List<ExcelImportDTO> data;
        private String yearMonth;
        private boolean updateExisting;
    }
}
