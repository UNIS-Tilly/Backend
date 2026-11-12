package com.unis.tilly.user.dto;

import jakarta.validation.constraints.Pattern;

// 4.3: workEndTime이 null이면 해제. @Pattern은 null을 통과시키므로 해제 요청은 그대로 유효하다.
public record WorkEndTimeRequest(
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "퇴근 시간은 HH:mm 형식이어야 합니다.") String workEndTime) {
}
