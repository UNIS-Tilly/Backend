package com.unis.tilly.ai.dto;

import java.time.LocalDate;
import java.util.List;

// 9.2 주간 인사이트 생성 입력. 수치(stats)는 서버가 계산해서 주고, AI가 쓴 제목·소제목 숫자는 이 값으로 검증한다 (D-112)
// days에는 COMPLETED 기록만 넣는다 (8장 4번). 구성은 insight 단위에서 확정한다 (NOTICE-028)
public record WeeklyInsightInput(
        LocalDate weekStart,
        LocalDate weekEnd,
        String weekLabel,
        WeekStats stats,
        WeekStats previousWeekStats,   // 지난 주 비교 자료가 없으면 null → 비교 문구 금지
        boolean insufficientContent,   // 메모·답변이 하나도 없어 할 일 제목만으로 분석 (D-54)
        List<DayInput> days) {

    public record WeekStats(int totalCount, int completedCount) {

        // 완수율(%) — 전체가 0이면 0
        public int completionRate() {
            return totalCount == 0 ? 0 : Math.round(completedCount * 100f / totalCount);
        }
    }

    public record DayInput(LocalDate date, List<AiTaskInput> tasks, List<AnswerInput> answers) {
    }

    public record AnswerInput(String question, String answer) {
    }
}
