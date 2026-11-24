package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.RecordService;

import java.time.LocalDate;

// 6.7 하루 회고 조회 (없으면 reflection = null)
public record RecordReflectionResponse(LocalDate date, String reflection) {

    public static RecordReflectionResponse from(RecordService.DailyReflectionView view) {
        return new RecordReflectionResponse(view.date(), view.content());
    }
}
