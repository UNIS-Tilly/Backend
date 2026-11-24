package com.unis.tilly.insight.dto;

import java.util.List;

// 발행 시점에 저장하는 주간 달성·업무 흐름 (스냅샷, D-112, D-128). 날짜는 문자열(YYYY-MM-DD)로 저장·응답한다.
public final class InsightSnapshot {

    private InsightSnapshot() {
    }

    // 월~일 7일 전체의 업무 달성: 그날(배정일) 할 일 수와 그 중 완료한 수 (D-128, D-135)
    public record Achievement(int totalCount, int completedCount, List<Day> days) {
    }

    public record Day(String date, String dayOfWeek, int completedCount, int totalCount) {
    }

    // 같은 달 1주차~해당 주차의 주별 완료/전체
    public record FlowWeek(String weekLabel, String weekStart, String weekEnd, int completedCount, int totalCount) {
    }
}
