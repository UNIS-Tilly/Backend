package com.unis.tilly.insight.service;

import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// 잠금 판정 (API_SPEC 1.5, D-130, D-137): 날짜가 속한 주의 인사이트 상태로 정한다
@ExtendWith(MockitoExtension.class)
class InsightLockServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 10, 19);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 10, 30, 0);

    @Mock
    private WeeklyInsightRepository insightRepository;

    private InsightLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new InsightLockService(insightRepository, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    @Test
    void IN03_인사이트가_없는_주는_잠기지_않는다() {
        given(insightRepository.findByUserIdAndWeekStart(1L, WEEK_START)).willReturn(Optional.empty());

        assertThat(lockService.isLocked(1L, LocalDate.of(2026, 10, 21))).isFalse();
    }

    @Test
    void IN03_주의_어느_날짜든_그_주의_인사이트로_판정한다() {
        WeeklyInsight generating = new WeeklyInsight(1L, WEEK_START, NOW.minusMinutes(1));
        given(insightRepository.findByUserIdAndWeekStart(1L, WEEK_START)).willReturn(Optional.of(generating));

        assertThat(lockService.isLocked(1L, LocalDate.of(2026, 10, 19))).isTrue();   // 월
        assertThat(lockService.isLocked(1L, LocalDate.of(2026, 10, 25))).isTrue();   // 일
    }

    @Test
    void IN03_GENERATING_발행됨은_잠기고_FAILED와_만료된_GENERATING은_풀린다() {
        WeeklyInsight published = new WeeklyInsight(1L, WEEK_START, NOW.minusMinutes(1));
        published.publish(NOW, false, "t", "s", new WeeklyInsight.Summary(3, 0, 0, 0, 0, 0), "{}", "[]");
        WeeklyInsight failed = new WeeklyInsight(2L, WEEK_START, NOW.minusMinutes(1));
        failed.fail();
        WeeklyInsight expired = new WeeklyInsight(3L, WEEK_START, NOW.minusMinutes(11));
        given(insightRepository.findByUserIdAndWeekStart(1L, WEEK_START)).willReturn(Optional.of(published));
        given(insightRepository.findByUserIdAndWeekStart(2L, WEEK_START)).willReturn(Optional.of(failed));
        given(insightRepository.findByUserIdAndWeekStart(3L, WEEK_START)).willReturn(Optional.of(expired));

        assertThat(lockService.isLocked(1L, LocalDate.of(2026, 10, 22))).isTrue();
        assertThat(lockService.isLocked(2L, LocalDate.of(2026, 10, 22))).isFalse();
        assertThat(lockService.isLocked(3L, LocalDate.of(2026, 10, 22))).isFalse();
    }
}
