package com.unis.tilly.insight.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

// 주(週) 계산 규칙: 월요일 시작~일요일 종료, 주차 표기는 월요일이 속한 달 기준 (API_SPEC 1.1, D-55)
public final class WeekRules {

    private WeekRules() {
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate weekEnd(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }

    // 월요일이 속한 달의 N주차 (그 달 첫 월요일이 속한 주 = 1주차). 예: 2026-10-19 → "10월 3주차"
    public static String label(LocalDate weekStart) {
        int weekOfMonth = (weekStart.getDayOfMonth() - 1) / 7 + 1;
        return weekStart.getMonthValue() + "월 " + weekOfMonth + "주차";
    }

    // 같은 달의 1주차부터 해당 주차까지(해당 주 포함)의 월요일 목록 (weeklyFlow, D-112)
    public static List<LocalDate> monthWeeksUpTo(LocalDate weekStart) {
        LocalDate monday = weekStart.withDayOfMonth(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        List<LocalDate> weeks = new ArrayList<>();
        while (!monday.isAfter(weekStart)) {
            weeks.add(monday);
            monday = monday.plusWeeks(1);
        }
        return weeks;
    }
}
