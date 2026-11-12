package com.unis.tilly.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank(message = "인가 코드가 필요합니다.") String code) {
}
