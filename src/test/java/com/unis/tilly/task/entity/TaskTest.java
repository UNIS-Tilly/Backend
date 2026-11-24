package com.unis.tilly.task.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 5.0 할 일 모델: 마감은 "오늘 기준 남은 일수" 표시용 (2026-11-24 변경, 이월·지연 재구성 없음)
class TaskTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void HM02_마감까지_남은_일수를_돌려준다() {
        assertThat(task(TODAY, LocalDateTime.of(2026, 10, 24, 18, 0)).dueInDays(TODAY)).isEqualTo(3);
    }

    @Test
    void HM02_오늘_마감이면_0이다() {
        assertThat(task(TODAY, TODAY.atTime(18, 0)).dueInDays(TODAY)).isZero();
    }

    @Test
    void HM02_마감이_지났으면_음수다() {
        assertThat(task(TODAY, LocalDateTime.of(2026, 10, 17, 18, 0)).dueInDays(TODAY)).isEqualTo(-4);
    }

    @Test
    void HM02_남은_일수는_마감_시각이_아니라_마감_날짜_기준이다() {
        // 마감 시각(00:00)이 이미 지난 당일도 0, 다음 날 23:59 마감은 하루 남음
        assertThat(task(TODAY, TODAY.atStartOfDay()).dueInDays(TODAY)).isZero();
        assertThat(task(TODAY, TODAY.plusDays(1).atTime(23, 59)).dueInDays(TODAY)).isEqualTo(1);
    }

    @Test
    void HM02_마감이_없거나_완료한_할_일은_null이다() {
        assertThat(task(TODAY, null).dueInDays(TODAY)).isNull();

        Task done = task(TODAY, LocalDateTime.of(2026, 10, 24, 18, 0));
        done.complete(TODAY);
        assertThat(done.dueInDays(TODAY)).isNull();

        done.uncomplete();
        assertThat(done.dueInDays(TODAY)).isEqualTo(3);
    }

    @Test
    void HM02_남은_일수는_배정일이_아니라_오늘이_기준이다() {
        // 지난 날짜(배정일 10/15)의 할 일을 보더라도 마감까지는 "오늘" 기준으로 센다
        Task task = task(LocalDate.of(2026, 10, 15), LocalDateTime.of(2026, 10, 23, 9, 0));

        assertThat(task.dueInDays(TODAY)).isEqualTo(2);
    }

    @Test
    void HM04_이미_완료된_할_일을_다시_완료해도_최초_완료일을_유지한다() {
        Task task = task(TODAY, null);
        task.complete(TODAY);
        task.complete(TODAY.plusDays(2));

        assertThat(task.getCompletedDate()).isEqualTo(TODAY);

        task.uncomplete();
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedDate()).isNull();
    }

    private Task task(LocalDate date, LocalDateTime dueDate) {
        return new Task(1L, "제목", date, dueDate);
    }
}
