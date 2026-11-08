package com.unis.tilly.user.dto;

import jakarta.validation.constraints.NotBlank;

// 4.5
public record WithdrawalRequest(@NotBlank(message = "확인용 이메일이 필요합니다.") String confirmEmail) {
}
