package com.example.demo.api.inventory.log;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    // View - 로그 목록 (관리자 전용)
    @GetMapping("/inventory/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public String logList(Model model) {
        model.addAttribute("menu", "logs");
        return "inventory/log-list";
    }

    // API - 로그 검색
    @GetMapping("/api/inventory/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(activityLogService.searchLogs(username, action, targetType, page, size));
    }

    // API - 오래된 로그 삭제 (기본 90일 이전)
    @DeleteMapping("/api/inventory/logs/old")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteOldLogs(
            @RequestParam(defaultValue = "90") int daysToKeep) {
        int deleted = activityLogService.deleteOldLogs(daysToKeep);

        Map<String, Object> result = new HashMap<>();
        result.put("message", daysToKeep + "일 이전 로그 " + deleted + "건이 삭제되었습니다.");
        result.put("deletedCount", deleted);
        return ResponseEntity.ok(result);
    }
}
