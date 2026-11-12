package com.unis.tilly.ai.dto;

import java.time.LocalDate;
import java.util.List;

// 6.5 확장 질문 생성 입력: 그날 보이는 할 일 제목·완료 여부·메모 (API_SPEC 6.5 [제안], 질문 생성 단위에서 확정)
public record QuestionGenerationInput(LocalDate date, List<AiTaskInput> tasks) {
}
