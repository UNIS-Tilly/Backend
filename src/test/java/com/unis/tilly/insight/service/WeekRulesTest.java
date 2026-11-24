package com.unis.tilly.insight.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 주 계산 (API_SPEC 1.1, D-55): 월~일, 주차는 월요일이 속한 달 기준
class WeekRulesTest {

    @Test
    void IN01_주의_시작은_월요일이고_끝은_일요일이다() {
        LocalDate monday = LocalDate.of(2026, 10, 19);

        assertThat(WeekRules.weekStart(monday)).isEqualTo(monday);
        assertThat(WeekRules.weekStart(LocalDate.of(2026, 10, 21))).isEqualTo(monday);
        assertThat(WeekRules.weekStart(LocalDate.of(2026, 10, 25))).isEqualTo(monday);   // 일요일
        assertThat(WeekRules.weekStart(LocalDate.of(2026, 10, 26))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(WeekRules.weekEnd(monday)).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void IN01_주차는_월요일이_속한_달_기준이다() {
        assertThat(WeekRules.label(LocalDate.of(2026, 10, 19))).isEqualTo("10월 3주차");
        assertThat(WeekRules.label(LocalDate.of(2026, 10, 26))).isEqualTo("10월 4주차");   // 10/26~11/1
        assertThat(WeekRules.label(LocalDate.of(2026, 11, 2))).isEqualTo("11월 1주차");
        assertThat(WeekRules.label(LocalDate.of(2026, 10, 5))).isEqualTo("10월 1주차");   // 그 달 첫 월요일
    }

    @Test
    void IN01_주차_경계는_월요일_날짜로_7일씩_바뀐다() {
        // 월요일 날짜 1~7일 = 1주차, 8~14일 = 2주차 …
        assertThat(WeekRules.label(LocalDate.of(2026, 6, 1))).isEqualTo("6월 1주차");
        assertThat(WeekRules.label(LocalDate.of(2026, 6, 8))).isEqualTo("6월 2주차");
        assertThat(WeekRules.label(LocalDate.of(2026, 6, 29))).isEqualTo("6월 5주차");
    }

    @Test
    void IN05_업무_흐름은_같은_달_1주차부터_해당_주차까지다() {
        assertThat(WeekRules.monthWeeksUpTo(LocalDate.of(2026, 10, 19)))
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19));
        assertThat(WeekRules.monthWeeksUpTo(LocalDate.of(2026, 11, 2))).containsExactly(LocalDate.of(2026, 11, 2));
        assertThat(WeekRules.monthWeeksUpTo(LocalDate.of(2026, 10, 26)))
                .hasSize(4).last().isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void IN05_흐름의_주차_라벨은_순서대로_1주차부터다() {
        List<String> labels = WeekRules.monthWeeksUpTo(LocalDate.of(2026, 10, 26)).stream()
                .map(WeekRules::label).toList();

        assertThat(labels).containsExactly("10월 1주차", "10월 2주차", "10월 3주차", "10월 4주차");
    }
}
