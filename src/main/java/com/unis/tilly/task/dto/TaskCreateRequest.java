package com.unis.tilly.task.dto;

import jakarta.validation.constraints.NotBlank;

// 5.3: date(배정일)·dueDate(마감일)는 생략하거나 null이면 "없음". 형식 검사는 서비스에서 한다.
public record TaskCreateRequest(
        @NotBlank(message = "할 일 제목은 비워둘 수 없습니다.") String title,
        String date,
        String dueDate) {
}
