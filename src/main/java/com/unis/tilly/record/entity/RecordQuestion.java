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

// 하루 질문(확장 질문)과 답변. 질문 생성은 AI 연동 단계에서 추가한다.
@Entity
@Table(name = "record_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    // 1~3
    @Column(name = "question_order", nullable = false)
    private int order;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(length = 1000)
    private String answer;

    @Column(nullable = false)
    private boolean skipped;

    public RecordQuestion(Long recordId, int order, String text) {
        this.recordId = recordId;
        this.order = order;
        this.text = text;
    }

    public void changeAnswer(String answer, boolean skipped) {
        this.answer = answer;
        this.skipped = skipped;
    }

    // 전체 교체에서 빠진 질문은 비운다 (사용자 결정 2026-11-12)
    public void clearAnswer() {
        changeAnswer(null, false);
    }
}
