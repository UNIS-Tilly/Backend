package com.unis.tilly.auth.dto;

// status: LOGGED_IN(기존 회원, accessToken/userId 포함) / SIGNUP_REQUIRED(미가입, signupToken/email 포함)
public record LoginResponse(String status, String accessToken, Long userId, String signupToken, String email) {

    public static LoginResponse loggedIn(String accessToken, Long userId) {
        return new LoginResponse("LOGGED_IN", accessToken, userId, null, null);
    }

    public static LoginResponse signupRequired(String signupToken, String email) {
        return new LoginResponse("SIGNUP_REQUIRED", null, null, signupToken, email);
    }
}
