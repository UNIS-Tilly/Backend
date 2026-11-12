package com.unis.tilly.record.dto;

import com.unis.tilly.record.service.ArchiveService;

import java.time.LocalDate;
import java.util.List;

// 7.1
public record ArchiveCalendarResponse(int year, int month, List<DayResponse> days) {

    public static ArchiveCalendarResponse from(ArchiveService.MonthlyCalendar calendar) {
        return new ArchiveCalendarResponse(calendar.year(), calendar.month(),
                calendar.days().stream().map(d -> new DayResponse(d.date(), d.hasRecord())).toList());
    }

    public record DayResponse(LocalDate date, boolean hasRecord) {
    }
}
