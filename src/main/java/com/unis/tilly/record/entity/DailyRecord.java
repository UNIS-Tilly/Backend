package com.unis.tilly.record.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import com.unis.tilly.record.enums.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 날짜별 기록 상태 (java.lang.Record와 충돌을 피해 DailyRecord, PROJECT_STRUCTURE 3.2)
@Entity
@Table(name = "daily_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public DailyRecord(Long userId, LocalDate date, RecordStatus status) {
        this.userId = userId;
        this.date = date;
        this.status = status;
    }

    // D-95: 수동 임시저장은 NOT_STARTED → DRAFT. DRAFT·COMPLETED는 그대로 (내려가지 않음)
    public void markDraft() {
        if (status == RecordStatus.NOT_STARTED) {
            this.status = RecordStatus.DRAFT;
        }
    }

    // D-45: 이미 완료된 기록은 최초 완료 시각을 유지한다. 처음 완료되면 true
    public boolean complete(LocalDateTime now) {
        if (status == RecordStatus.COMPLETED) {
            return false;
        }
        this.status = RecordStatus.COMPLETED;
        this.completedAt = now;
        return true;
    }
}
