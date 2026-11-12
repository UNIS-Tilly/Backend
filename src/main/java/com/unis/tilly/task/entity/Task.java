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
import java.time.temporal.ChronoUnit;

// 5.0 할 일 모델 (D-102~D-106)
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

    // 배정일. null이면 미완료인 동안 생성일부터 항상 노출된다.
    private LocalDate date;

    // 마감일. 노출 여부에는 영향이 없고 지연 일수 계산에만 쓴다.
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    // 업무별 메모(할 일당 1개). 저장은 기록 임시저장·완료의 taskMemos로 한다 (record 도메인).
    @Column(length = 500)
    private String memo;

    public Task(Long userId, String title, LocalDate date, LocalDate dueDate) {
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

    public void changeDueDate(LocalDate dueDate) {
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

    // day 시점에 아직 끝나지 않았는지 (과거 날짜 재구성: 그 뒤에 완료했으면 그날엔 미완료)
    public boolean isIncompleteOn(LocalDate day) {
        return completedDate == null || completedDate.isAfter(day);
    }

    // 5.1 노출 규칙: 완료 목록은 완료일 기준, 미완료 목록은 배정일(없으면 생성일) 이후 끝나기 전까지
    public boolean isVisibleOn(LocalDate day) {
        if (day.equals(completedDate)) {
            return true;
        }
        if (!isIncompleteOn(day)) {
            return false;
        }
        LocalDate visibleFrom = date != null ? date : getCreatedAt().toLocalDate();
        return !visibleFrom.isAfter(day);
    }

    // D-104: 마감일이 지났는데 day 시점에 끝나지 않았으면 지난 일수, 아니면 null
    public Integer overdueDaysOn(LocalDate day) {
        if (dueDate == null || !isIncompleteOn(day) || !day.isAfter(dueDate)) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(dueDate, day);
    }
}
