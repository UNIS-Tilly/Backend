package com.unis.tilly.record.dto;

import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.service.RecordService;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 6.3
public record RecordDraftResponse(LocalDate date, RecordStatus status, LocalDateTime savedAt) {

    public static RecordDraftResponse from(RecordService.DraftResult result) {
        return new RecordDraftResponse(result.date(), result.status(), result.savedAt());
    }
}
