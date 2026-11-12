package com.unis.tilly.auth.service;

import com.unis.tilly.global.config.CookieProperties;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.global.security.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 실제 Redis(Testcontainers)로 TokenService의 발급/재발급 회전/로그아웃 블랙리스트를 확인한다.
@Testcontainers
class TokenServiceTest {

    private static final String SECRET = "test-only-jwt-signing-key-not-for-production-use-0123456789";

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    private TokenService newTokenService() {
        CookieProperties cookieProperties = new CookieProperties();
        cookieProperties.setSecure(false);
        cookieProperties.setSameSite("Lax");
        return new TokenService(new JwtProvider(SECRET), redisTemplate, cookieProperties);
    }

    @Test
    void 토큰_발급_시_Redis에_refresh_jti가_저장된다() {
        TokenService tokenService = newTokenService();
        Long userId = 1001L;

        TokenService.IssuedTokens tokens = tokenService.issueTokens(userId);

        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isNotBlank();
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void 재발급하면_Refresh가_회전되고_이전_Refresh는_재사용할_수_없다() {
        TokenService tokenService = newTokenService();
        Long userId = 1002L;
        TokenService.IssuedTokens first = tokenService.issueTokens(userId);

        TokenService.IssuedTokens second = tokenService.reissue(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatThrownBy(() -> tokenService.reissue(first.refreshToken()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 로그아웃하면_refresh_키가_삭제되고_access_jti가_블랙리스트에_들어간다() {
        TokenService tokenService = newTokenService();
        Long userId = 1003L;
        tokenService.issueTokens(userId);

        AuthenticatedUser user = new AuthenticatedUser(userId, "some-jti-1003", Instant.now().plusSeconds(60));
        tokenService.logout(user);

        assertThat(redisTemplate.hasKey("refresh:" + userId)).isFalse();
        assertThat(redisTemplate.hasKey("blacklist:some-jti-1003")).isTrue();
    }

    @Test
    void 쿠키_설정_메서드가_Set_Cookie_헤더를_추가한다() {
        TokenService tokenService = newTokenService();
        HttpServletResponse response = mock(HttpServletResponse.class);

        tokenService.setRefreshCookie(response, "dummy-refresh-token");

        verify(response).addHeader(eq("Set-Cookie"), contains("refreshToken=dummy-refresh-token"));
    }
}
