package com.unis.tilly.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 4.2
public record NicknameUpdateRequest(
        @NotBlank(message = "닉네임은 비워둘 수 없습니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.") String nickname) {
}
