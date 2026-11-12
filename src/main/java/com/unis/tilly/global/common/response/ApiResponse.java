package com.unis.tilly.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.unis.tilly.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonPropertyOrder({"success", "errorCode", "message", "data"})
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final boolean success;
    @JsonInclude(JsonInclude.Include.NON_NULL) // 성공 응답 시 errorCode 생략
    private final String errorCode;
    private final String message;
    private final T data;

    // 데이터가 있는 성공 응답
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, null, message, data);
    }

    // 데이터 없는 성공 응답
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    // 실패 응답 (ErrorCode 기본 메시지)
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.getMessage(), null);
    }

    // 실패 응답 (메시지 직접 작성: @Valid 실패 등)
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.name(), message, null);
    }

    // 실패 응답 (스프링 표준 예외처럼 ErrorCode에 없는 경우)
    public static <T> ApiResponse<T> fail(String errorCode, String message) {
        return new ApiResponse<>(false, errorCode, message, null);
    }
}
