package com.unis.tilly.auth.service;

import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.auth.dto.LoginResponse;
import com.unis.tilly.auth.dto.RegisterResponse;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.global.security.JwtProvider;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 구글 실제 연동. 가입 세부 API는 D-31/D-88~D-90을 따른다(기존 프로젝트 코드 재사용 안 함).
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOidcClient googleOidcClient;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;

    public LoginOutcome loginWithGoogle(String code) {
        GoogleOidcClient.GoogleUserInfo userInfo = googleOidcClient.exchangeCodeForUserInfo(code);

        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw new CustomException(ErrorCode.GOOGLE_AUTH_FAILED, "구글 계정에서 이메일을 확인할 수 없습니다.");
        }

        return userService.findByProvider(SocialProvider.GOOGLE, userInfo.sub())
                .map(this::loginExistingUser)
                .orElseGet(() -> issueSignupToken(userInfo));
    }

    // D-97: 인증 흐름·토큰·쿠키는 그대로 두고 선택 필드 gender·birthYear만 함께 저장한다.
    public RegisterOutcome register(String signupToken, String nickname, Gender gender, Integer birthYear) {
        Claims claims = parseSignupToken(signupToken);

        SocialProvider provider = SocialProvider.valueOf(claims.get("provider", String.class));
        String providerUserId = claims.get("providerUserId", String.class);
        String email = claims.get("email", String.class);

        User user = userService.register(email, nickname, provider, providerUserId, gender, birthYear);
        TokenService.IssuedTokens tokens = tokenService.issueTokens(user.getId());

        return new RegisterOutcome(new RegisterResponse(tokens.accessToken(), user.getId()), tokens.refreshToken());
    }

    private LoginOutcome loginExistingUser(User user) {
        TokenService.IssuedTokens tokens = tokenService.issueTokens(user.getId());
        return new LoginOutcome(LoginResponse.loggedIn(tokens.accessToken(), user.getId()), tokens.refreshToken());
    }

    private LoginOutcome issueSignupToken(GoogleOidcClient.GoogleUserInfo userInfo) {
        JwtProvider.GeneratedToken signupToken = jwtProvider.generateSignupToken(
                SocialProvider.GOOGLE.name(), userInfo.sub(), userInfo.email());
        return new LoginOutcome(LoginResponse.signupRequired(signupToken.token(), userInfo.email()), null);
    }

    private Claims parseSignupToken(String signupToken) {
        try {
            return jwtProvider.parseSignupToken(signupToken);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_SIGNUP_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_SIGNUP_TOKEN);
        }
    }

    // refreshToken이 null이면(가입 필요 상태) 컨트롤러가 쿠키를 설정하지 않는다.
    public record LoginOutcome(LoginResponse response, String refreshToken) {
    }

    public record RegisterOutcome(RegisterResponse response, String refreshToken) {
    }
}
