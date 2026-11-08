package com.unis.tilly.user.controller;

import com.unis.tilly.auth.service.TokenService;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.global.security.AuthenticatedUser;
import com.unis.tilly.user.dto.NicknameUpdateRequest;
import com.unis.tilly.user.dto.NicknameUpdateResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
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

    // ST-01
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getMe(@AuthenticationPrincipal AuthenticatedUser user) {
        User found = userService.getUser(user.userId());
        return ApiResponse.success(SuccessMessage.OK.message(), UserInfoResponse.from(found));
    }

    // 4.2
    @PatchMapping("/me/nickname")
    public ApiResponse<NicknameUpdateResponse> updateNickname(@AuthenticationPrincipal AuthenticatedUser user,
                                                                 @RequestBody @Valid NicknameUpdateRequest request) {
        User updated = userService.updateNickname(user.userId(), request.nickname());
        return ApiResponse.success(SuccessMessage.NICKNAME_UPDATED.message(), new NicknameUpdateResponse(updated.getNickname()));
    }

    // 4.3
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
