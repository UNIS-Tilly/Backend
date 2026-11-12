package com.unis.tilly.record.dto;

import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.service.RecordService;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 6.4 (level = 갱신된 성장 레벨, D-101)
public record RecordCompleteResponse(LocalDate date, RecordStatus status, LocalDateTime completedAt, int level) {

    public static RecordCompleteResponse from(RecordService.CompleteResult result) {
        return new RecordCompleteResponse(result.date(), result.status(), result.completedAt(), result.level());
    }
}
