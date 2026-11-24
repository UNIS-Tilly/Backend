package com.unis.tilly.task.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 5.0 할 일 모델 (D-102~D-106, 2026-11-24 변경: 배정일 하루에만 노출, 마감은 남은 일수 표시용)
@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String title;

    // 배정일. 할 일은 이 날짜에만 노출된다(이월 없음).
    @Column(nullable = false)
    private LocalDate date;

    // 마감 일시(분 단위, Asia/Seoul). 노출 여부와 무관하고 "오늘 기준 며칠 남았는지" 표시에만 쓴다.
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    // 업무별 메모(할 일당 1개). 저장은 기록 임시저장·완료의 taskMemos로 한다 (record 도메인).
    @Column(length = 500)
    private String memo;

    public Task(Long userId, String title, LocalDate date, LocalDateTime dueDate) {
        this.userId = userId;
        this.title = title;
        this.date = date;
        this.dueDate = dueDate;
        this.completed = false;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeDate(LocalDate date) {
        this.date = date;
    }

    public void changeDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    // D-106: 메모는 기록 임시저장·완료로만 바뀐다. 빈 메모는 null로 저장
    public void changeMemo(String memo) {
        this.memo = memo;
    }

    // 이미 완료된 할 일에 완료를 다시 보내면 최초 완료일을 유지한다.
    public void complete(LocalDate today) {
        if (!completed) {
            this.completed = true;
            this.completedDate = today;
        }
    }

    public void uncomplete() {
        this.completed = false;
        this.completedDate = null;
    }

    // 마감까지 남은 일수 = 마감 날짜 − 오늘. 양수=남음, 0=오늘 마감, 음수=지남.
    // 마감 시각은 표시용이고 날짜 기준으로 센다. 마감이 없거나 이미 완료했으면 null.
    public Integer dueInDays(LocalDate today) {
        if (dueDate == null || completed) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, dueDate.toLocalDate());
    }
}
