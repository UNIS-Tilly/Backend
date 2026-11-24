package com.unis.tilly.insight.dto;

import java.time.LocalDate;
import java.util.List;

// 9.5 GET /insights/weekly/list — 발행된 주만. 항목 = 제목·소제목·기간 (D-141)
public record InsightListResponse(List<Item> items, int page, int size, long totalElements, boolean hasNext) {

    public record Item(Long insightId, LocalDate weekStart, LocalDate weekEnd, String weekLabel,
                       String title, String subtitle) {
    }
}
