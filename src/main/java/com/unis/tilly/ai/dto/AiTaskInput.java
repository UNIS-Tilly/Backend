package com.unis.tilly.ai.dto;

// AI 입력용 할 일 (제목·완료 여부·메모만. 사용자 ID·이메일 등 개인정보는 넣지 않는다)
public record AiTaskInput(String title, boolean completed, String memo) {
}
