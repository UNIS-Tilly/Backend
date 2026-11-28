package com.unis.tilly.user.dto;

import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.Competency;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.JobGroup;
import com.unis.tilly.user.service.UserService;

import java.time.format.DateTimeFormatter;
import java.util.List;

// 4.1 내 정보 (4.2 응답도 같은 형식). competencies가 비어 있으면 프로필 입력 전
public record UserInfoResponse(String nickname, String email, String workEndTime,
                               Gender gender, Integer age,
                               JobGroup currentJobGroup, String currentJobDetail, JobGroup targetJobGroup,
                               List<Competency> competencies, int level) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static UserInfoResponse from(UserService.UserProfile profile) {
        User user = profile.user();
        String workEndTime = user.getWorkEndTime() == null ? null : user.getWorkEndTime().format(TIME_FORMAT);
        return new UserInfoResponse(user.getNickname(), user.getEmail(), workEndTime,
                user.getGender(), user.getAge(),
                user.getCurrentJobGroup(), user.getCurrentJobDetail(), user.getTargetJobGroup(),
                profile.competencies(), user.getLevel());
    }
}
