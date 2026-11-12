package com.unis.tilly.auth.service;

import com.unis.tilly.global.config.CookieProperties;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.global.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// Access/Refresh 발급·재발급·로그아웃을 담당한다. JWT 생성·검증은 JwtProvider, 저장은 Redis(StringRedisTemplate)로 분리한다.
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";
    static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final CookieProperties cookieProperties;

    public IssuedTokens issueTokens(Long userId) {
        JwtProvider.GeneratedToken access = jwtProvider.generateAccessToken(userId);
        JwtProvider.GeneratedToken refresh = jwtProvider.generateRefreshToken(userId);

        storeRefreshJti(userId, refresh.jti());

        return new IssuedTokens(access.token(), refresh.token());
    }

    public IssuedTokens reissue(String refreshToken) {
        Claims claims;
        try {
            claims = jwtProvider.parseRefreshToken(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = Long.valueOf(claims.getSubject());
        String presentedJti = claims.getId();
        String storedJti = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);

        if (storedJti == null || !storedJti.equals(presentedJti)) {
            // 이미 사용된(회전된) Refresh가 재사용됨 — 탈취 의심
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        return issueTokens(userId);
    }

    public void logout(AuthenticatedUser user) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + user.userId());

        Duration remaining = Duration.between(Instant.now(), user.expiresAt());
        if (remaining.isPositive()) {
            redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + user.jti(), "1", remaining);
        }
    }

    public void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader("Set-Cookie", buildCookie(refreshToken, JwtProvider.REFRESH_TOKEN_VALIDITY).toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }

    private void storeRefreshJti(Long userId, String jti) {
        redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + userId, jti, JwtProvider.REFRESH_TOKEN_VALIDITY);
    }

    public record IssuedTokens(String accessToken, String refreshToken) {
    }
}
