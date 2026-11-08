package com.unis.tilly.auth.service;

import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.security.JwtProvider;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.service.UserService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private GoogleOidcClient googleOidcClient;
    @Mock
    private UserService userService;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(googleOidcClient, userService, jwtProvider, tokenService);
    }

    @Test
    void 기존_회원이면_로그인_토큰을_바로_발급한다() {
        given(googleOidcClient.exchangeCodeForUserInfo("code"))
                .willReturn(new GoogleOidcClient.GoogleUserInfo("sub-1", "user@example.com", true));
        User existingUser = userWithId(10L);
        given(userService.findByProvider(SocialProvider.GOOGLE, "sub-1")).willReturn(Optional.of(existingUser));
        given(tokenService.issueTokens(10L)).willReturn(new TokenService.IssuedTokens("access", "refresh"));

        AuthService.LoginOutcome outcome = authService.loginWithGoogle("code");

        assertThat(outcome.response().status()).isEqualTo("LOGGED_IN");
        assertThat(outcome.response().accessToken()).isEqualTo("access");
        assertThat(outcome.response().userId()).isEqualTo(10L);
        assertThat(outcome.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void 미가입이면_가입_토큰만_발급하고_Access_Refresh는_발급하지_않는다() {
        given(googleOidcClient.exchangeCodeForUserInfo("code"))
                .willReturn(new GoogleOidcClient.GoogleUserInfo("sub-2", "new@example.com", true));
        given(userService.findByProvider(SocialProvider.GOOGLE, "sub-2")).willReturn(Optional.empty());
        given(jwtProvider.generateSignupToken("GOOGLE", "sub-2", "new@example.com"))
                .willReturn(new JwtProvider.GeneratedToken("signup-jwt", "jti-1", Instant.now().plusSeconds(1800)));

        AuthService.LoginOutcome outcome = authService.loginWithGoogle("code");

        assertThat(outcome.response().status()).isEqualTo("SIGNUP_REQUIRED");
        assertThat(outcome.response().signupToken()).isEqualTo("signup-jwt");
        assertThat(outcome.response().email()).isEqualTo("new@example.com");
        assertThat(outcome.refreshToken()).isNull();
        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    void 구글_계정에_이메일이_없으면_GOOGLE_AUTH_FAILED() {
        given(googleOidcClient.exchangeCodeForUserInfo("code"))
                .willReturn(new GoogleOidcClient.GoogleUserInfo("sub-3", null, null));

        assertThatThrownBy(() -> authService.loginWithGoogle("code"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 가입_토큰으로_회원가입하면_Access_Refresh를_발급한다() {
        var claims = Jwts.claims()
                .add("provider", "GOOGLE")
                .add("providerUserId", "sub-4")
                .add("email", "signup@example.com")
                .build();
        given(jwtProvider.parseSignupToken("signup-token")).willReturn(claims);

        User savedUser = userWithId(20L);
        given(userService.register("signup@example.com", "틸리", SocialProvider.GOOGLE, "sub-4"))
                .willReturn(savedUser);
        given(tokenService.issueTokens(20L)).willReturn(new TokenService.IssuedTokens("access2", "refresh2"));

        AuthService.RegisterOutcome outcome = authService.register("signup-token", "틸리");

        assertThat(outcome.response().accessToken()).isEqualTo("access2");
        assertThat(outcome.response().userId()).isEqualTo(20L);
        assertThat(outcome.refreshToken()).isEqualTo("refresh2");
    }

    private User userWithId(Long id) {
        User user = new User("user@example.com", "닉네임", SocialProvider.GOOGLE, "sub");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
