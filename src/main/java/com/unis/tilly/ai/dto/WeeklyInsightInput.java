package com.unis.tilly.ai.dto;

import com.unis.tilly.user.enums.Competency;
import com.unis.tilly.user.enums.JobGroup;

import java.time.LocalDate;
import java.util.List;

// 9.2 주간 인사이트 생성 입력. 수치(stats)는 서버가 계산해서 주고, AI가 쓴 제목·소제목 숫자는 이 값으로 검증한다 (D-112)
// days에는 COMPLETED 기록만 넣는다 (8장 4번). profile은 강점 판단 기준(D-132, D-133)
public record WeeklyInsightInput(
        LocalDate weekStart,
        LocalDate weekEnd,
        String weekLabel,
        WeekStats stats,
        WeekStats previousWeekStats,   // 지난 주 비교 자료가 없으면 null → 비교 문구 금지
        boolean insufficientContent,   // 메모·답변·회고가 하나도 없어 할 일 제목만으로 분석 (D-54)
        List<DayInput> days,
        Profile profile,
        AiCurrentJob currentJob) {     // 재직 직군·세부 직군 (맥락 참고용, 재직 직군이 없으면 null)

    public record WeekStats(int totalCount, int completedCount) {

        // 완수율(%) — 전체가 0이면 0
        public int completionRate() {
            return totalCount == 0 ? 0 : Math.round(completedCount * 100f / totalCount);
        }
    }

    // reflection: 그날 하루 회고 (없으면 null)
    public record DayInput(LocalDate date, List<AiTaskInput> tasks, List<AnswerInput> answers, String reflection) {
    }

    public record AnswerInput(String question, String answer) {
    }

    // 강점 판단 기준 (D-132, D-133): 희망 직군(없으면 null)과 사용자가 선택한 역량. 닉네임·이메일·성별·나이는 넣지 않는다 (RULES 7.7.6)
    // 확장 질문 입력(QuestionGenerationInput)도 같은 형태를 쓴다 (질문도 희망 직군·선택 역량을 참고)
    public record Profile(String targetJobGroupCode, String targetJobGroupLabel, List<CompetencyInfo> competencies) {

        // 희망 직군(없으면 null)과 선택한 역량으로 만든다
        public static Profile of(JobGroup target, List<Competency> competencies) {
            return new Profile(
                    target == null ? null : target.name(),
                    target == null ? null : target.getLabel(),
                    competencies.stream()
                            .map(c -> new CompetencyInfo(c.name(), c.getLabel(), c.getDescription()))
                            .toList());
        }
    }

    public record CompetencyInfo(String code, String label, String definition) {
    }
}
