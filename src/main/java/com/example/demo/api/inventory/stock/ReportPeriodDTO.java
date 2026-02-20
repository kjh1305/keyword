package com.example.demo.api.inventory.stock;

import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportPeriodDTO {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String startDateStr;
    private String endDateStr;
    private String displayLabel;

    public static ReportPeriodDTO fromEntity(ReportPeriod period) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startStr = period.getStartDate() != null ? period.getStartDate().format(fmt) : "";
        String endStr = period.getEndDate() != null ? period.getEndDate().format(fmt) : "진행중";

        return ReportPeriodDTO.builder()
                .id(period.getId())
                .name(period.getName())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus())
                .startDateStr(startStr)
                .endDateStr(endStr)
                .displayLabel(period.getName() + " (" + startStr + " ~ " + endStr + ")")
                .build();
    }
}
