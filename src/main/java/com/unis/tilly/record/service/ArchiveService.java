package com.unis.tilly.record.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.record.entity.DailyRecord;
import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.repository.DailyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 7.1 AR-01, AR-02 (D-110): 그 달의 모든 날짜에 기록 유무만
@Service
@RequiredArgsConstructor
public class ArchiveService {

    // "기록 있음" = 수동 임시저장(DRAFT) 또는 퇴근 완료(COMPLETED). 자동저장만 된 날은 제외
    private static final Set<RecordStatus> RECORDED = Set.of(RecordStatus.DRAFT, RecordStatus.COMPLETED);

    private final DailyRecordRepository recordRepository;

    public MonthlyCalendar getCalendar(Long userId, int year, int month) {
        YearMonth yearMonth = toYearMonth(year, month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        Set<LocalDate> recordedDates = recordRepository
                .findAllByUserIdAndDateBetweenAndStatusIn(userId, from, to, RECORDED).stream()
                .map(DailyRecord::getDate)
                .collect(Collectors.toSet());

        List<Day> days = from.datesUntil(to.plusDays(1))
                .map(date -> new Day(date, recordedDates.contains(date)))
                .toList();
        return new MonthlyCalendar(year, month, days);
    }

    private YearMonth toYearMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "연도 또는 월이 올바르지 않습니다.");
        }
    }

    public record Day(LocalDate date, boolean hasRecord) {
    }

    public record MonthlyCalendar(int year, int month, List<Day> days) {
    }
}
