package com.unis.tilly.notification.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 13.0 알림 문구: 조사(가/이), 오늘/내일 표기
class NotificationMessagesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 28);

    @Test
    void 받침이_없으면_가를_붙인다() {
        assertThat(NotificationMessages.dueSoonBody("주간 회의 자료 수집 및 자료 정리", TODAY.atTime(11, 0), TODAY))
                .isEqualTo("'주간 회의 자료 수집 및 자료 정리'가 오늘 11:00에 마감이에요.");
    }

    @Test
    void 받침이_있으면_이를_붙인다() {
        assertThat(NotificationMessages.dueSoonBody("보고서 작성", TODAY.atTime(9, 5), TODAY))
                .isEqualTo("'보고서 작성'이 오늘 09:05에 마감이에요.");
    }

    @Test
    void 한글이_아니면_이_가를_붙인다() {
        assertThat(NotificationMessages.subjectParticle("API v2")).isEqualTo("이(가)");
        assertThat(NotificationMessages.subjectParticle("")).isEqualTo("이(가)");
    }

    @Test
    void 자정을_넘기면_내일로_표기한다() {
        LocalDateTime due = TODAY.plusDays(1).atTime(0, 10);
        assertThat(NotificationMessages.dueSoonBody("회의", due, TODAY)).isEqualTo("'회의'가 내일 00:10에 마감이에요.");
    }

    @Test
    void 인사이트_문구는_주차_라벨을_쓴다() {
        assertThat(NotificationMessages.insightBody("11월 2주차")).isEqualTo("11월 2주차 분석 결과를 확인해 보세요.");
    }
}
