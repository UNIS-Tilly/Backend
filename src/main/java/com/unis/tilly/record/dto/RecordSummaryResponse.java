package com.unis.tilly.record.dto;

import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.service.RecordService;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 6.1
public record RecordSummaryResponse(LocalDate date, RecordStatus status, boolean locked, int memoCount,
                                    LocalDateTime completedAt) {

    public static RecordSummaryResponse from(RecordService.Summary summary) {
        return new RecordSummaryResponse(summary.date(), summary.status(), summary.locked(),
                summary.memoCount(), summary.completedAt());
    }
}
