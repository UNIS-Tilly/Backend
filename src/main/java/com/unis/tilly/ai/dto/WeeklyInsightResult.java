package com.unis.tilly.ai.dto;

import com.unis.tilly.ai.enums.AiEvidenceType;

import java.time.LocalDate;
import java.util.List;

// AI가 만든 주간 인사이트 (서버 검증을 통과한 값만 돌려준다)
public record WeeklyInsightResult(String title, String subtitle,
                                  List<Strength> strengths, List<Improvement> improvements) {

    public record Strength(String title, String description, List<String> tags, List<Evidence> evidences) {
    }

    // 보완 하나당 추천 행동 하나 (#18). action은 다음 할 일 제목이 되므로 20자 이내 (D-57)
    public record Improvement(String title, String description, List<Evidence> evidences, String action) {
    }

    public record Evidence(LocalDate date, AiEvidenceType type) {
    }
}
