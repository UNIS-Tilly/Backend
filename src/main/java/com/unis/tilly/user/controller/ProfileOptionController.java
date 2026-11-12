package com.unis.tilly.user.controller;

import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.common.response.SuccessMessage;
import com.unis.tilly.user.dto.ProfileOptionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 4.3 직군·역량·성별 선택지 (인증 필요, D-99)
@RestController
@RequestMapping("/api/meta")
public class ProfileOptionController {

    private static final ProfileOptionResponse OPTIONS = ProfileOptionResponse.create();

    @GetMapping("/profile-options")
    public ApiResponse<ProfileOptionResponse> getProfileOptions() {
        return ApiResponse.success(SuccessMessage.OK.message(), OPTIONS);
    }
}
