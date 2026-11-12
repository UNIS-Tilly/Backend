package com.unis.tilly.record.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 6.3 업무별 메모 (할 일당 1개, 500자). 빈 문자열/null은 "메모 없음"
public record TaskMemoRequest(
        @NotNull(message = "taskId가 필요합니다.") Long taskId,
        @Size(max = 500, message = "메모는 500자 이하여야 합니다.") String memo) {
}
