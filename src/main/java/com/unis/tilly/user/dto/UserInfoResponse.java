package com.unis.tilly.user.dto;

import com.unis.tilly.user.entity.User;

import java.time.format.DateTimeFormatter;

// 4.1
public record UserInfoResponse(String nickname, String email, String workEndTime) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static UserInfoResponse from(User user) {
        String workEndTime = user.getWorkEndTime() == null ? null : user.getWorkEndTime().format(TIME_FORMAT);
        return new UserInfoResponse(user.getNickname(), user.getEmail(), workEndTime);
    }
}
