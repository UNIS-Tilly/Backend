package com.unis.tilly.auth.controller;

import com.unis.tilly.auth.dto.DevLoginResponse;
import com.unis.tilly.auth.service.TokenService;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// local 프로파일에서만 빈이 등록된다 (그 외 프로파일에서는 매핑 자체가 없어 404, NOTICE-030/D-91).
// 구글·카카오 없이 테스트 유저의 Access/Refresh를 발급하지만, 실제 로그인과 같은 JWT·Redis 경로를 그대로 쓴다.
@RestController
@RequestMapping("/api/auth")
@Profile("local")
@RequiredArgsConstructor
public class DevAuthController {

    // user 도메인(다음 단위)에서 실제 users 테이블이 생기기 전까지 쓰는 고정 테스트 사용자 ID.
    private static final Long DEV_TEST_USER_ID = 1L;

    private final TokenService tokenService;

    @PostMapping("/dev-login")
    public ApiResponse<DevLoginResponse> devLogin(HttpServletResponse response) {
        TokenService.IssuedTokens tokens = tokenService.issueTokens(DEV_TEST_USER_ID);
        tokenService.setRefreshCookie(response, tokens.refreshToken());

        return ApiResponse.success(SuccessMessage.OK.message(), new DevLoginResponse(tokens.accessToken(), DEV_TEST_USER_ID));
    }
}
