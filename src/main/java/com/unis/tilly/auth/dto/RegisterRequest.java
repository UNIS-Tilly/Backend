package com.unis.tilly.auth.dto;

import com.unis.tilly.user.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3.2 가입. gender·birthYear는 선택(생략·null 허용) — 닉네임만 보내는 기존 요청도 그대로 동작 (D-97)
public record RegisterRequest(
        @NotBlank(message = "가입 토큰이 필요합니다.") String signupToken,
        @NotBlank(message = "닉네임은 비워둘 수 없습니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.") String nickname,
        Gender gender,
        Integer birthYear) {
}
