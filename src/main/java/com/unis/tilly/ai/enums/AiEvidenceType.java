package com.unis.tilly.ai.enums;

// AI가 돌려주는 근거 기록 종류 (API_SPEC 1.4 EvidenceType과 같은 값). REFLECTION은 하루 회고 복원(2026-11-24)으로 다시 추가
public enum AiEvidenceType {
    MEMO,
    QUESTION_ANSWER,
    REFLECTION,
    TASK
}
