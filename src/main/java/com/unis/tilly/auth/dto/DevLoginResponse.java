package com.unis.tilly.auth.dto;

// local 프로파일 전용 개발용 로그인 응답. Refresh Token은 응답 body가 아니라 HttpOnly 쿠키로 전달된다.
public record DevLoginResponse(String accessToken, Long userId) {
}
