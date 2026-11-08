package com.unis.tilly.global.exception;

import com.unis.tilly.global.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인 예외: ErrorCode를 그대로 응답에 반영한다.
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode, e.getMessage()));
    }

    // @Valid 검증 실패: 메시지를 직접 채운다 (fieldErrors 필드는 두지 않음).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, message));
    }

    // 요청 본문을 읽을 수 없음 (JSON 파싱 실패, 잘못된 enum 값 등)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."));
    }

    // 쿼리/경로 파라미터 형식 오류 (예: date 파싱 실패)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getStatus())
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, "요청 파라미터 형식이 올바르지 않습니다."));
    }

    // 그 외 예기치 못한 오류: 상세는 서버 로그에만 남기고, 응답에는 일반 메시지만 반환한다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
