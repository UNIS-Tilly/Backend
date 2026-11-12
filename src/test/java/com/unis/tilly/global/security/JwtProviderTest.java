package com.unis.tilly.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-only-jwt-signing-key-not-for-production-use-0123456789";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET);

    @Test
    void Access_토큰을_발급하고_파싱하면_userId와_type이_맞다() {
        JwtProvider.GeneratedToken token = jwtProvider.generateAccessToken(42L);

        Claims claims = jwtProvider.parseAccessToken(token.token());

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("type", String.class)).isEqualTo("ACCESS");
        assertThat(claims.getId()).isEqualTo(token.jti());
    }

    @Test
    void Refresh_토큰을_Access_파서로_파싱하면_거부된다() {
        JwtProvider.GeneratedToken refresh = jwtProvider.generateRefreshToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseAccessToken(refresh.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void Access_토큰을_Refresh_파서로_파싱하면_거부된다() {
        JwtProvider.GeneratedToken access = jwtProvider.generateAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseRefreshToken(access.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰은_ExpiredJwtException을_던진다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
        String expired = Jwts.builder()
                .subject("1")
                .claim("type", "ACCESS")
                .id("expired-jti")
                .issuedAt(new Date(past.getTime() - 1000))
                .expiration(past)
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtProvider.parseAccessToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 다른_키로_서명된_토큰은_거부된다() {
        SecretKey otherKey = Keys.hmacShaKeyFor("different-signing-key-also-at-least-32-bytes!!".getBytes(StandardCharsets.UTF_8));
        String tampered = Jwts.builder()
                .subject("1")
                .claim("type", "ACCESS")
                .id("tampered-jti")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> jwtProvider.parseAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }
}
