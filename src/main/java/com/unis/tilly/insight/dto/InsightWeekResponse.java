package com.unis.tilly.insight.dto;

import com.unis.tilly.insight.enums.EvidenceType;
import com.unis.tilly.insight.enums.InsightStatus;
import com.unis.tilly.insight.enums.StarPart;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 9.1 GET /insights/weekly — 한 주의 인사이트 (NOT_PUBLISHED·FAILED는 eligibility, GENERATING은 둘 다 null, PUBLISHED는 report)
public record InsightWeekResponse(LocalDate weekStart, LocalDate weekEnd, String weekLabel, InsightStatus status,
                                  Eligibility eligibility, Report report) {

    // eligible: 기록 조건 충족(퇴근 완료 3일 이상 + 분석할 내용 있음, D-51·D-136) / requestable: 지금 요청 가능한 시점(D-139)
    public record Eligibility(boolean eligible, boolean requestable, int completedDays, int requiredDays) {
    }

    public record Report(Long insightId, LocalDateTime generatedAt, List<String> warnings, String title, String subtitle,
                         Summary summary, InsightSnapshot.Achievement weeklyAchievement,
                         List<InsightSnapshot.FlowWeek> weeklyFlow, List<Strength> strengths,
                         List<Improvement> improvements, List<Action> actions) {
    }

    public record Summary(int recordedDays, int memoCount, int reflectionCount, int answerCount) {
    }

    public record Strength(Long id, String title, String description, String competencyCode, List<String> tags,
                           List<Evidence> evidences) {
    }

    public record Improvement(Long id, String title, String description, StarPart starPart, List<Evidence> evidences) {
    }

    public record Evidence(LocalDate date, EvidenceType type) {
    }

    public record Action(Long id, Long improvementId, String text) {
    }
}
