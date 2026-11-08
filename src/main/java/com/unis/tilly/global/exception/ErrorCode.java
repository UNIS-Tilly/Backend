package com.unis.tilly.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Default
    INTERNAL_SERVER_ERROR(500, "서버 내부 에러가 발생했습니다."),
    BAD_REQUEST(400, "잘못된 요청입니다."),

    // Auth - 401 (소셜 로그인·가입 관련 코드는 인증 단계에서 추가)
    ACCESS_TOKEN_REQUIRED(401, "Access Token이 필요합니다."),
    INVALID_ACCESS_TOKEN(401, "유효하지 않은 Access Token입니다."),
    EXPIRED_ACCESS_TOKEN(401, "만료된 Access Token입니다."),
    REFRESH_TOKEN_REQUIRED(401, "Refresh Token이 필요합니다."),
    INVALID_REFRESH_TOKEN(401, "유효하지 않은 Refresh Token입니다."),
    EXPIRED_REFRESH_TOKEN(401, "만료된 Refresh Token입니다."),

    // User
    USER_NOT_FOUND(404, "존재하지 않는 사용자입니다."),
    EMAIL_MISMATCH(400, "입력한 이메일이 일치하지 않습니다."),

    // Task
    TASK_NOT_FOUND(404, "존재하지 않는 할 일입니다."),
    TASK_LIMIT_EXCEEDED(400, "하루에 추가할 수 있는 할 일 개수를 초과했습니다."),

    // Record
    QUESTION_NOT_FOUND(404, "존재하지 않는 질문입니다."),
    RECORD_LOCKED(409, "해당 주의 인사이트가 발행되어 수정할 수 없습니다."),
    RECORD_EMPTY(422, "기록이 비어 있어 질문을 생성할 수 없습니다."),

    // AI
    AI_GENERATION_FAILED(502, "AI 응답 생성에 실패했습니다."),
    AI_TIMEOUT(504, "AI 응답 시간이 초과되었습니다."),

    // Insight
    INSIGHT_NOT_FOUND(404, "존재하지 않는 인사이트입니다."),
    ACTION_NOT_FOUND(404, "존재하지 않는 추천 행동입니다."),
    INSIGHT_WEEK_NOT_ENDED(422, "아직 끝나지 않은 주입니다."),
    INSUFFICIENT_RECORDS(422, "분석할 수 있는 기록이 부족합니다."),
    INSIGHT_ALREADY_PUBLISHED(409, "이미 발행된 주입니다."),
    NO_ACTION_SELECTED(400, "반영할 행동을 선택하지 않았습니다.");

    private final int status;
    private final String message;
}
