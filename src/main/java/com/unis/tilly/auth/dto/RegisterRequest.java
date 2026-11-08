package com.unis.tilly.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "가입 토큰이 필요합니다.") String signupToken,
        @NotBlank(message = "닉네임은 비워둘 수 없습니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.") String nickname) {
}
