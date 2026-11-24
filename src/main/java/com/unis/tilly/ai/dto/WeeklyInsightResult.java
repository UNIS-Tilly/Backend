package com.unis.tilly.ai.dto;

import com.unis.tilly.ai.enums.AiEvidenceType;
import com.unis.tilly.ai.enums.AiStarPart;

import java.time.LocalDate;
import java.util.List;

// AI가 만든 주간 인사이트 (서버 검증을 통과한 값만 돌려준다)
public record WeeklyInsightResult(String title, String subtitle,
                                  List<Strength> strengths, List<Improvement> improvements) {

    // competencyCode: 사용자가 선택한 역량 중 근거가 있을 때만(없으면 null, D-134)
    public record Strength(String title, String description, String competencyCode, List<String> tags,
                           List<Evidence> evidences) {
    }

    // 보완 하나당 추천 행동 하나 (#18). action은 다음 할 일 제목이 되므로 20자 이내 (D-57)
    // starPart: 보완이 필요한 STAR 단계 (D-134)
    public record Improvement(String title, String description, AiStarPart starPart, List<Evidence> evidences,
                              String action) {
    }

    public record Evidence(LocalDate date, AiEvidenceType type) {
    }
}
