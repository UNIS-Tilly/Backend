package com.unis.tilly.insight.dto;

import jakarta.validation.constraints.NotBlank;

// 9.2 POST /insights/weekly — date는 분석할 주 안의 날짜(보통 오늘). 형식 검사는 서비스에서 한다.
public record InsightRequest(@NotBlank(message = "date가 필요합니다.") String date) {
}
