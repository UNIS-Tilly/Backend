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
import java.util.UUID;

// Access/Refresh JWT 생성·검증을 전담한다. Redis 연동(저장·블랙리스트)은 TokenService가 담당한다.
@Component
public class JwtProvider {

    public static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofMinutes(30);
    public static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(14);

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final SecretKey key;

    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public GeneratedToken generateAccessToken(Long userId) {
        return generate(userId, TYPE_ACCESS, ACCESS_TOKEN_VALIDITY);
    }

    public GeneratedToken generateRefreshToken(Long userId) {
        return generate(userId, TYPE_REFRESH, REFRESH_TOKEN_VALIDITY);
    }

    public Claims parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public Claims parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private GeneratedToken generate(Long userId, String type, Duration validity) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(validity);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

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
