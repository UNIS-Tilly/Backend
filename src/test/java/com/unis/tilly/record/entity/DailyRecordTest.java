package com.unis.tilly.record.entity;

import com.unis.tilly.record.enums.RecordStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// D-95 상태 전이, D-45 최초 완료 시각 유지
class DailyRecordTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 21);

    @Test
    void RC12_수동_임시저장은_NOT_STARTED만_DRAFT로_바꾸고_COMPLETED는_유지한다() {
        DailyRecord notStarted = new DailyRecord(1L, DATE, RecordStatus.NOT_STARTED);
        notStarted.markDraft();
        assertThat(notStarted.getStatus()).isEqualTo(RecordStatus.DRAFT);

        DailyRecord completed = new DailyRecord(1L, DATE, RecordStatus.DRAFT);
        completed.complete(LocalDateTime.of(2026, 10, 21, 18, 0));
        completed.markDraft();
        assertThat(completed.getStatus()).isEqualTo(RecordStatus.COMPLETED);
    }

    @Test
    void RC13_처음_완료할_때만_true이고_최초_완료_시각을_유지한다() {
        DailyRecord record = new DailyRecord(1L, DATE, RecordStatus.DRAFT);

        assertThat(record.complete(LocalDateTime.of(2026, 10, 21, 18, 0))).isTrue();
        assertThat(record.complete(LocalDateTime.of(2026, 10, 22, 9, 0))).isFalse();
        assertThat(record.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 18, 0));
    }
}
