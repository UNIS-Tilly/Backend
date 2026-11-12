package com.unis.tilly.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

// Access/Refresh/가입 JWT 생성·검증을 전담한다. Redis 연동(저장·블랙리스트)은 TokenService가 담당한다.
@Component
public class JwtProvider {

    public static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofMinutes(30);
    public static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(14);
    public static final Duration SIGNUP_TOKEN_VALIDITY = Duration.ofMinutes(30);

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";
    private static final String TYPE_SIGNUP = "SIGNUP";

    private final SecretKey key;

    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public GeneratedToken generateAccessToken(Long userId) {
        return generate(String.valueOf(userId), TYPE_ACCESS, ACCESS_TOKEN_VALIDITY, Map.of());
    }

    public GeneratedToken generateRefreshToken(Long userId) {
        return generate(String.valueOf(userId), TYPE_REFRESH, REFRESH_TOKEN_VALIDITY, Map.of());
    }

    // 가입 전 사용자(소셜 인증은 됐지만 TILLY 회원이 아닌 상태)에게 발급한다. 저장소 없이 stateless로 검증한다.
    public GeneratedToken generateSignupToken(String provider, String providerUserId, String email) {
        String subject = provider + ":" + providerUserId;
        return generate(subject, TYPE_SIGNUP, SIGNUP_TOKEN_VALIDITY, Map.of(
                "provider", provider,
                "providerUserId", providerUserId,
                "email", email));
    }

    public Claims parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public Claims parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public Claims parseSignupToken(String token) {
        return parse(token, TYPE_SIGNUP);
    }

    private GeneratedToken generate(String subject, String type, Duration validity, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(validity);
        String jti = UUID.randomUUID().toString();

        var builder = Jwts.builder()
                .subject(subject)
                .claim(CLAIM_TYPE, type)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        extraClaims.forEach(builder::claim);

        String token = builder.signWith(key).compact();

        return new GeneratedToken(token, jti, expiresAt);
    }

    private Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("토큰 종류가 올바르지 않습니다.");
        }
        return claims;
    }

    public record GeneratedToken(String token, String jti, Instant expiresAt) {
    }
}
