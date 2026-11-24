package com.unis.tilly.record.entity;

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

// 하루 회고: 사용자·날짜당 1건(2,000자 이하). 기록 행(DailyRecord)이 없는 날에도 자동저장될 수 있어 (userId, date)로 식별한다.
// 내용이 비면 행을 두지 않는다 (RecordService에서 삭제).
@Entity
@Table(name = "daily_reflections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReflection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 2000)
    private String content;

    public DailyReflection(Long userId, LocalDate date, String content) {
        this.userId = userId;
        this.date = date;
        this.content = content;
    }

    public void changeContent(String content) {
        this.content = content;
    }
}
