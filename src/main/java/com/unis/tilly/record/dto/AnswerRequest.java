package com.unis.tilly.record.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 6.3 질문 답변 (1,000자). skipped = 건너뛰기를 누른 질문
public record AnswerRequest(
        @NotNull(message = "questionId가 필요합니다.") Long questionId,
        @Size(max = 1000, message = "답변은 1,000자 이하여야 합니다.") String answer,
        Boolean skipped) {
}
