package com.unis.tilly.insight.dto;

import com.unis.tilly.insight.enums.InsightStatus;

import java.time.LocalDate;
import java.util.List;

// 9.4 GET /insights/weekly/latest — 홈 카드: 가장 최근에 발행된 인사이트의 제목·소제목·추천 행동.
// 발행본이 없으면 status = NOT_PUBLISHED, 나머지는 null / 빈 배열. 추천 행동은 보여주기만 하고 할 일로 반영하지 않는다 (D-147)
public record InsightLatestResponse(Long insightId, LocalDate weekStart, LocalDate weekEnd, String weekLabel,
                                    InsightStatus status, String title, String subtitle, List<ActionText> actions) {

    public record ActionText(String text) {
    }
}
