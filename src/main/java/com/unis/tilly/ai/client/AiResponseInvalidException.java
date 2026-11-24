package com.unis.tilly.ai.client;

// AI 응답이 형식·규칙 검증을 통과하지 못함 → 다시 생성을 요청한다 (D-57).
// 메시지에는 사용자 기록이나 AI 응답 원문을 넣지 않는다 (로그 노출 방지).
class AiResponseInvalidException extends RuntimeException {

    AiResponseInvalidException(String reason) {
        super(reason);
    }
}
