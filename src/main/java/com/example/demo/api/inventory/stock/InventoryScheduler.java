package com.example.demo.api.inventory.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryScheduler {

    private final InventoryService inventoryService;

    /**
     * 매월 1일 새벽 3시에 새 달 재고 자동 생성
     */
    @Scheduled(cron = "0 0 3 1 * *")
    public void autoInitializeMonthlyInventory() {
        String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        log.info("새 달 재고 자동 생성 시작: {}", yearMonth);

        try {
            inventoryService.initializeMonthlyInventory(yearMonth);
            log.info("새 달 재고 자동 생성 완료: {}", yearMonth);
        } catch (Exception e) {
            log.error("새 달 재고 자동 생성 실패: {}", e.getMessage(), e);
        }
    }
}
