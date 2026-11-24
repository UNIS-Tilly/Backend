package com.unis.tilly.insight.entity;

import com.unis.tilly.insight.enums.InsightStatus;
import com.unis.tilly.insight.service.InsightLockService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 인사이트 상태 전이와 잠금·만료 판정 (D-137, 사용자 결정 2026-11-24: GENERATING 10분 초과는 실패)
class WeeklyInsightTest {

    private static final LocalDateTime REQUESTED = LocalDateTime.of(2026, 10, 24, 10, 0, 0);
    private static final Duration TIMEOUT = InsightLockService.GENERATION_TIMEOUT;

    @Test
    void IN03_분석을_시작하면_GENERATING이고_그_주를_잠근다() {
        WeeklyInsight insight = new WeeklyInsight(1L, LocalDate.of(2026, 10, 19), REQUESTED);

        assertThat(insight.getStatus()).isEqualTo(InsightStatus.GENERATING);
        assertThat(insight.locksWeek(REQUESTED.plusMinutes(1), TIMEOUT)).isTrue();
    }

    @Test
    void IN03_GENERATING이_10분을_넘으면_만료되어_잠기지_않는다() {
        WeeklyInsight insight = new WeeklyInsight(1L, LocalDate.of(2026, 10, 19), REQUESTED);

        assertThat(insight.isExpiredGenerating(REQUESTED.plusMinutes(10), TIMEOUT)).isFalse();   // 정확히 10분은 아직
        assertThat(insight.isExpiredGenerating(REQUESTED.plusMinutes(10).plusSeconds(1), TIMEOUT)).isTrue();
        assertThat(insight.locksWeek(REQUESTED.plusMinutes(11), TIMEOUT)).isFalse();
    }

    @Test
    void IN03_FAILED는_잠기지_않고_다시_GENERATING으로_되돌릴_수_있다() {
        WeeklyInsight insight = new WeeklyInsight(1L, LocalDate.of(2026, 10, 19), REQUESTED);
        insight.fail();

        assertThat(insight.getStatus()).isEqualTo(InsightStatus.FAILED);
        assertThat(insight.locksWeek(REQUESTED.plusMinutes(1), TIMEOUT)).isFalse();

        insight.restart(REQUESTED.plusHours(1));
        assertThat(insight.getStatus()).isEqualTo(InsightStatus.GENERATING);
        assertThat(insight.getRequestedAt()).isEqualTo(REQUESTED.plusHours(1));
    }

    @Test
    void IN03_발행되면_PUBLISHED이고_오래_지나도_계속_잠긴다() {
        WeeklyInsight insight = new WeeklyInsight(1L, LocalDate.of(2026, 10, 19), REQUESTED);
        insight.publish(REQUESTED.plusMinutes(2), true, "제목", "소제목",
                new WeeklyInsight.Summary(4, 7, 3, 6, 12, 9), "{}", "[]");

        assertThat(insight.getStatus()).isEqualTo(InsightStatus.PUBLISHED);
        assertThat(insight.isInsufficientContent()).isTrue();
        assertThat(insight.getTitle()).isEqualTo("제목");
        assertThat(insight.getTotalCount()).isEqualTo(12);
        assertThat(insight.isExpiredGenerating(REQUESTED.plusDays(30), TIMEOUT)).isFalse();   // 발행본은 만료 개념이 없다
        assertThat(insight.locksWeek(REQUESTED.plusDays(30), TIMEOUT)).isTrue();
    }
}
