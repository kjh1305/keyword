package com.example.demo.api.inventory.excel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelImportDTO {
    private Integer rowNumber;
    private String productName;
    private String category;
    private BigDecimal initialStock;
    private BigDecimal usedQuantity;
    private BigDecimal orderQuantity;       // 주문수량
    private String orderQuantityRaw;        // 주문수량 원본 (3+1, 1box 등)

    // JSON 전송용 문자열 리스트
    private List<String> receivedDateStrings;
    private List<String> expiryDateStrings;

    private String unit;
    private Integer minQuantity;
    private String note;

    // 내부 사용용 LocalDate 리스트 (JSON 직렬화 제외)
    @JsonIgnore
    private List<LocalDate> receivedDates;
    @JsonIgnore
    private List<LocalDate> expiryDates;

    // receivedDates setter - 문자열 리스트도 함께 설정
    public void setReceivedDates(List<LocalDate> dates) {
        this.receivedDates = dates;
        if (dates != null) {
            this.receivedDateStrings = dates.stream()
                    .map(d -> d.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .collect(Collectors.toList());
        }
    }

    // expiryDates setter - 문자열 리스트도 함께 설정
    public void setExpiryDates(List<LocalDate> dates) {
        this.expiryDates = dates;
        if (dates != null) {
            this.expiryDateStrings = dates.stream()
                    .map(d -> d.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .collect(Collectors.toList());
        }
    }

    // receivedDates getter - 문자열에서 파싱
    public List<LocalDate> getReceivedDates() {
        if (receivedDates != null && !receivedDates.isEmpty()) {
            return receivedDates;
        }
        if (receivedDateStrings != null && !receivedDateStrings.isEmpty()) {
            return receivedDateStrings.stream()
                    .map(s -> LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE))
                    .collect(Collectors.toList());
        }
        return null;
    }

    // expiryDates getter - 문자열에서 파싱
    public List<LocalDate> getExpiryDates() {
        if (expiryDates != null && !expiryDates.isEmpty()) {
            return expiryDates;
        }
        if (expiryDateStrings != null && !expiryDateStrings.isEmpty()) {
            return expiryDateStrings.stream()
                    .map(s -> LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE))
                    .collect(Collectors.toList());
        }
        return null;
    }

    // 단일 값 반환 (호환성 유지) - 가장 빠른 날짜
    public LocalDate getExpiryDate() {
        List<LocalDate> dates = getExpiryDates();
        if (dates == null || dates.isEmpty()) return null;
        return dates.stream().min(LocalDate::compareTo).orElse(null);
    }

    public LocalDate getReceivedDate() {
        List<LocalDate> dates = getReceivedDates();
        if (dates == null || dates.isEmpty()) return null;
        return dates.stream().min(LocalDate::compareTo).orElse(null);
    }
}
