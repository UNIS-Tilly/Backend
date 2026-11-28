package com.unis.tilly.ai.dto;

// AI 입력용 재직 직군: 대분류 표시명 + 사용자가 직접 입력한 세부 직군(없으면 null).
// 기록 속 업무를 이해하는 맥락 참고용이며 강점 판단 기준이 아니다
public record AiCurrentJob(String jobGroupLabel, String detail) {

    // 재직 직군이 없으면 null
    public static AiCurrentJob of(String jobGroupLabel, String detail) {
        return jobGroupLabel == null ? null : new AiCurrentJob(jobGroupLabel, detail);
    }
}
