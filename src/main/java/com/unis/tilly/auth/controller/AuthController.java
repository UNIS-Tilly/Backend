package com.unis.tilly.auth.controller;

import com.unis.tilly.auth.dto.GoogleLoginRequest;
import com.unis.tilly.auth.dto.LoginResponse;
import com.unis.tilly.auth.dto.RegisterRequest;
import com.unis.tilly.auth.dto.RegisterResponse;
import com.unis.tilly.auth.dto.ReissueResponse;
import com.unis.tilly.auth.service.AuthService;
import com.unis.tilly.auth.service.TokenService;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.global.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 카카오 실제 연동은 별도 요청이 있을 때 추가한다 (D-30, 구글 우선).
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    // 쿠키 기반 재발급은 CSRF에 노출되므로, 단순 cross-site form/img로는 심을 수 없는 커스텀 헤더를 요구한다.
    private static final String CLIENT_HEADER = "X-Tilly-Client";
    private static final String CLIENT_HEADER_VALUE = "web";

    private final TokenService tokenService;
    private final AuthService authService;

    @PostMapping("/login/google")
    public ApiResponse<LoginResponse> loginWithGoogle(@RequestBody @Valid GoogleLoginRequest request,
                                                        HttpServletResponse response) {
        AuthService.LoginOutcome outcome = authService.loginWithGoogle(request.code());
        if (outcome.refreshToken() != null) {
            tokenService.setRefreshCookie(response, outcome.refreshToken());
        }
        return ApiResponse.success(SuccessMessage.OK.message(), outcome.response());
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@RequestBody @Valid RegisterRequest request,
                                                    HttpServletResponse response) {
        AuthService.RegisterOutcome outcome = authService.register(request.signupToken(), request.nickname());
        tokenService.setRefreshCookie(response, outcome.refreshToken());
        return ApiResponse.success(SuccessMessage.OK.message(), outcome.response());
    }

    // ST-02
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser user, HttpServletResponse response) {
        tokenService.logout(user);
        tokenService.clearRefreshCookie(response);
        return ApiResponse.success(SuccessMessage.LOGOUT.message());
    }

    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            @RequestHeader(name = CLIENT_HEADER, required = false) String client,
            HttpServletResponse response) {
        if (!CLIENT_HEADER_VALUE.equals(client)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "잘못된 요청입니다.");
        }
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REQUIRED);
        }

        TokenService.IssuedTokens tokens = tokenService.reissue(refreshToken);
        tokenService.setRefreshCookie(response, tokens.refreshToken());

        return ApiResponse.success(SuccessMessage.OK.message(), new ReissueResponse(tokens.accessToken()));
    }
}
