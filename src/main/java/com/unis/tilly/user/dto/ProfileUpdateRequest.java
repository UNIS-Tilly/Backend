package com.unis.tilly.user.dto;

import com.unis.tilly.user.enums.Competency;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.JobGroup;
import com.unis.tilly.user.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

// 4.2 프로필 전체 교체 (회원수정 페이지 1, 가입 직후 프로필 입력). 선택 항목은 null/생략 = 해제
// 존재하지 않는 ENUM 값은 역직렬화 단계에서 400 BAD_REQUEST. 역량 규칙은 서비스에서 검증
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임은 비워둘 수 없습니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.") String nickname,
        Gender gender,
        Integer age,
        JobGroup currentJobGroup,
        @Size(max = 30, message = "세부 직군은 30자 이하여야 합니다.") String currentJobDetail,
        JobGroup targetJobGroup,
        List<Competency> competencies) {

    // 앞뒤 공백을 제거하고 빈 값은 null로 다룬다 (길이 검증은 정리한 값 기준)
    public ProfileUpdateRequest {
        currentJobDetail = currentJobDetail == null || currentJobDetail.isBlank() ? null : currentJobDetail.strip();
    }

    public UserService.ProfileCommand toCommand() {
        return new UserService.ProfileCommand(nickname, gender, age, currentJobGroup, currentJobDetail,
                targetJobGroup, competencies);
    }
}
