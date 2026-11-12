package com.unis.tilly.task.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 5.0, 5.1 노출 규칙과 지연 일수(D-103, D-104)
class TaskTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 21);

    @Test
    void HM02_마감일이_지난_미완료는_지난_일수를_돌려준다() {
        Task task = task(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 17));

        assertThat(task.overdueDaysOn(D)).isEqualTo(4);
    }

    @Test
    void HM02_마감일_당일이나_마감일이_없으면_지연이_아니다() {
        assertThat(task(D, D).overdueDaysOn(D)).isNull();
        assertThat(task(D, null).overdueDaysOn(D)).isNull();
    }

    @Test
    void HM02_조회일에_이미_완료했으면_지연이_아니고_그_뒤에_완료했으면_그날은_지연이다() {
        Task task = task(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 17));
        task.complete(LocalDate.of(2026, 10, 20));

        assertThat(task.overdueDaysOn(D)).isNull();
        assertThat(task.overdueDaysOn(LocalDate.of(2026, 10, 19))).isEqualTo(2);
    }

    @Test
    void HM02_배정일이_지난_미완료는_이월되어_보이고_배정일_전에는_보이지_않는다() {
        Task task = task(LocalDate.of(2026, 10, 18), null);

        assertThat(task.isVisibleOn(D)).isTrue();
        assertThat(task.isVisibleOn(LocalDate.of(2026, 10, 17))).isFalse();
    }

    @Test
    void HM02_배정일이_없으면_생성일부터_보인다() {
        Task task = task(null, null);
        ReflectionTestUtils.setField(task, "createdAt", LocalDateTime.of(2026, 10, 20, 9, 0));

        assertThat(task.isVisibleOn(D)).isTrue();
        assertThat(task.isVisibleOn(LocalDate.of(2026, 10, 20))).isTrue();
        assertThat(task.isVisibleOn(LocalDate.of(2026, 10, 19))).isFalse();
    }

    @Test
    void HM04_완료한_할_일은_완료한_날에만_보인다() {
        Task task = task(LocalDate.of(2026, 10, 18), null);
        task.complete(D);

        assertThat(task.isVisibleOn(D)).isTrue();
        assertThat(task.isVisibleOn(LocalDate.of(2026, 10, 22))).isFalse();
        // 완료 전 날짜에는 미완료로 보인다(과거 재구성)
        assertThat(task.isVisibleOn(LocalDate.of(2026, 10, 19))).isTrue();
        assertThat(task.isIncompleteOn(LocalDate.of(2026, 10, 19))).isTrue();
    }

    @Test
    void HM04_이미_완료된_할_일을_다시_완료해도_최초_완료일을_유지한다() {
        Task task = task(D, null);
        task.complete(D);
        task.complete(D.plusDays(2));

        assertThat(task.getCompletedDate()).isEqualTo(D);

        task.uncomplete();
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedDate()).isNull();
    }

    private Task task(LocalDate date, LocalDate dueDate) {
        return new Task(1L, "제목", date, dueDate);
    }
}
