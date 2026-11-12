package com.unis.tilly.user.controller;

import com.unis.tilly.auth.service.TokenService;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.user.dto.ProfileUpdateRequest;
import com.unis.tilly.user.dto.UserInfoResponse;
import com.unis.tilly.user.dto.WithdrawalRequest;
import com.unis.tilly.user.dto.WorkEndTimeRequest;
import com.unis.tilly.user.dto.WorkEndTimeResponse;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;

    // ST-01 (4.1)
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getMe(@AuthenticationPrincipal AuthenticatedUser user) {
        UserService.UserProfile profile = userService.getProfile(user.userId());
        return ApiResponse.success(SuccessMessage.OK.message(), UserInfoResponse.from(profile));
    }

    // 4.2 회원수정 페이지 1 / 가입 직후 프로필 입력 (D-100, 닉네임 전용 API는 삭제됨)
    @PutMapping("/me/profile")
    public ApiResponse<UserInfoResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestBody @Valid ProfileUpdateRequest request) {
        UserService.UserProfile profile = userService.updateProfile(user.userId(), request.toCommand());
        return ApiResponse.success(SuccessMessage.PROFILE_UPDATED.message(), UserInfoResponse.from(profile));
    }

    // 4.4 회원수정 페이지 2
    @PutMapping("/me/work-end-time")
    public ApiResponse<WorkEndTimeResponse> updateWorkEndTime(@AuthenticationPrincipal AuthenticatedUser user,
                                                                 @RequestBody @Valid WorkEndTimeRequest request) {
        LocalTime workEndTime = request.workEndTime() == null ? null : LocalTime.parse(request.workEndTime());
        User updated = userService.updateWorkEndTime(user.userId(), workEndTime);
        return ApiResponse.success(SuccessMessage.WORK_END_TIME_UPDATED.message(), WorkEndTimeResponse.from(updated));
    }

    // ST-03
    @PostMapping("/me/withdrawal")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestBody @Valid WithdrawalRequest request,
                                        HttpServletResponse response) {
        userService.withdraw(user.userId(), request.confirmEmail());
        tokenService.logout(user);
        tokenService.clearRefreshCookie(response);
        return ApiResponse.success(SuccessMessage.WITHDRAWAL_COMPLETED.message());
    }
}
