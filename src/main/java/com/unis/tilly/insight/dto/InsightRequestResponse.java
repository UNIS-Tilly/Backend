package com.unis.tilly.insight.dto;

import com.unis.tilly.insight.enums.InsightStatus;

import java.time.LocalDate;

// 9.2 응답 202: 분석이 시작되었다(또는 이미 생성 중이다). 프론트는 GET /insights/weekly를 폴링한다
public record InsightRequestResponse(Long insightId, LocalDate weekStart, LocalDate weekEnd, String weekLabel,
                                     InsightStatus status) {
}
