package com.example.demo.api.inventory.stock;

import com.example.demo.api.inventory.order.StockOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final ReportPeriodRepository reportPeriodRepository;
    private final InventoryRepository inventoryRepository;
    private final StockOrderRepository stockOrderRepository;
    private final UsageLogRepository usageLogRepository;
    private final TransactionTemplate transactionTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                migrateYearMonthToReportPeriod();
            });
        } catch (Exception e) {
            log.error("ReportPeriod 마이그레이션 실패: {}", e.getMessage(), e);
        }
    }

    private void migrateYearMonthToReportPeriod() {
        // 멱등성: report_period 테이블이 이미 데이터가 있으면 스킵
        if (reportPeriodRepository.count() > 0) {
            log.info("ReportPeriod 데이터가 이미 존재합니다. 마이그레이션 스킵.");
            return;
        }

        List<String> yearMonths = inventoryRepository.findAllYearMonths();
        if (yearMonths.isEmpty()) {
            log.info("기존 yearMonth 데이터가 없습니다. 초기 기간을 생성합니다.");
            ReportPeriod openPeriod = ReportPeriod.builder()
                    .name(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .startDate(YearMonth.now().atDay(1))
                    .endDate(null)
                    .status("OPEN")
                    .build();
            reportPeriodRepository.save(openPeriod);
            return;
        }

        // yearMonths는 DESC 정렬이므로 역순(오래된 것부터)으로 처리
        String currentYM = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 1단계: 기존 yearMonth → CONFIRMED 기간 생성 및 인벤토리 연결 (현재 월 제외)
        ReportPeriod lastConfirmedPeriod = null;
        for (int i = yearMonths.size() - 1; i >= 0; i--) {
            String ym = yearMonths.get(i);
            if (ym.equals(currentYM)) {
                continue; // 현재 월은 OPEN 기간으로 별도 처리
            }
            YearMonth parsed = YearMonth.parse(ym);

            ReportPeriod period = ReportPeriod.builder()
                    .name(ym)
                    .startDate(parsed.atDay(1))
                    .endDate(parsed.atEndOfMonth())
                    .status("CONFIRMED")
                    .build();
            ReportPeriod saved = reportPeriodRepository.save(period);
            linkInventories(ym, saved);
            lastConfirmedPeriod = saved;
            log.info("CONFIRMED 기간 생성: {} ({}~{})", ym, parsed.atDay(1), parsed.atEndOfMonth());
        }

        // 2단계: 현재 월 OPEN 기간 생성 + 기존 인벤토리 연결 + 이월
        YearMonth now = YearMonth.now();
        ReportPeriod openPeriod = ReportPeriod.builder()
                .name(currentYM)
                .startDate(now.atDay(1))
                .endDate(null)
                .status("OPEN")
                .build();
        openPeriod = reportPeriodRepository.save(openPeriod);
        log.info("OPEN 기간 생성: {} ({}~)", currentYM, now.atDay(1));

        // 현재 월에 이미 존재하는 인벤토리가 있으면 OPEN 기간에 연결
        linkInventories(currentYM, openPeriod);

        // 마지막 CONFIRMED 기간의 인벤토리를 기반으로 OPEN 기간 인벤토리 이월
        if (lastConfirmedPeriod != null) {
            createCarryOverInventory(lastConfirmedPeriod, openPeriod);
        }

        log.info("yearMonth → ReportPeriod 마이그레이션 완료. 총 {} 기간 생성.", reportPeriodRepository.count());
    }

    private void linkInventories(String yearMonth, ReportPeriod period) {
        List<Inventory> inventories = inventoryRepository.findByYearMonth(yearMonth);
        for (Inventory inv : inventories) {
            inv.setReportPeriod(period);
            inventoryRepository.save(inv);
        }
        log.info("  {} 기간에 {} 재고 행 연결", yearMonth, inventories.size());
    }

    /**
     * 이전 CONFIRMED 기간의 남은재고를 새 OPEN 기간의 월초재고로 이월
     * (기존 initializeMonthlyInventory와 동일한 계산 로직)
     */
    private void createCarryOverInventory(ReportPeriod prevPeriod, ReportPeriod openPeriod) {
        List<Inventory> prevInventories = inventoryRepository.findByReportPeriodIdWithProduct(prevPeriod.getId());
        int created = 0;
        int linked = 0;

        LocalDate prevStartDate = prevPeriod.getStartDate();
        LocalDate prevEndDate = prevPeriod.getEndDate();
        String prevStartDateStr = prevStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String prevEndDateStr = prevEndDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        for (Inventory prevInv : prevInventories) {
            Long productId = prevInv.getProduct().getId();

            BigDecimal prevInitialStock = prevInv.getInitialStock() != null ? prevInv.getInitialStock() : BigDecimal.ZERO;
            BigDecimal prevUsedQuantity = prevInv.getUsedQuantity() != null ? prevInv.getUsedQuantity() : BigDecimal.ZERO;

            // 이전 기간 입고완료 수량
            BigDecimal prevCompletedStock = stockOrderRepository.sumCompletedQuantityByProductIdAndDateRange(
                    productId, prevStartDate, prevEndDate);
            if (prevCompletedStock == null) prevCompletedStock = BigDecimal.ZERO;

            // 이전 기간 운영 사용량 (UsageLog 기반)
            BigDecimal prevOperationalUsed = usageLogRepository.sumOperationalUsedByProductIdAndDateRange(
                    productId, prevStartDateStr, prevEndDateStr);
            if (prevOperationalUsed == null) prevOperationalUsed = BigDecimal.ZERO;

            // 새 기간 월초재고 = 이전 기간 월초재고 + 입고완료 - 보고용사용량 - 운영사용량
            BigDecimal newInitialStock = prevInitialStock.add(prevCompletedStock)
                    .subtract(prevUsedQuantity).subtract(prevOperationalUsed);

            // 이미 해당 yearMonth에 인벤토리가 있으면 initialStock 업데이트 + report_period 연결
            java.util.Optional<Inventory> existingOpt = inventoryRepository.findByProductIdAndYearMonth(productId, openPeriod.getName());
            if (existingOpt.isPresent()) {
                Inventory existing = existingOpt.get();
                existing.setReportPeriod(openPeriod);
                existing.setInitialStock(newInitialStock);
                existing.setUsedQuantity(BigDecimal.ZERO);
                inventoryRepository.save(existing);
                linked++;
                continue;
            }

            Inventory newInventory = Inventory.builder()
                    .product(prevInv.getProduct())
                    .yearMonth(openPeriod.getName())
                    .reportPeriod(openPeriod)
                    .initialStock(newInitialStock)
                    .usedQuantity(BigDecimal.ZERO)
                    .remainingStock(newInitialStock)
                    .expiryDate(prevInv.getExpiryDate())
                    .note(prevInv.getNote())
                    .build();

            inventoryRepository.save(newInventory);
            created++;
        }

        log.info("  OPEN 기간 '{}'에 인벤토리 이월 완료 (신규 생성: {}, 기존 연결: {})", openPeriod.getName(), created, linked);
    }
}
