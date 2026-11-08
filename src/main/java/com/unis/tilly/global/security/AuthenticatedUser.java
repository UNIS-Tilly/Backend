package com.unis.tilly.global.security;

import java.time.Instant;

// JwtAuthenticationFilter가 SecurityContext에 심는 인증 주체.
// 로그아웃 시 Access Token을 블랙리스트에 넣으려면 jti·만료 시각이 필요해서 userId만으로는 부족하다.
public record AuthenticatedUser(Long userId, String jti, Instant expiresAt) {
}
