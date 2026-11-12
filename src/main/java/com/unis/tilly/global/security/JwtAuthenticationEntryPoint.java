package com.unis.tilly.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 토큰 없이 인증이 필요한 요청이 들어왔을 때 (JwtAuthenticationFilter가 토큰 자체를 검증하는 경우와는 다름).
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Boot 4는 Jackson 2 ObjectMapper 빈을 기본 제공하지 않아 직접 둔다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(ErrorCode.ACCESS_TOKEN_REQUIRED.getStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ErrorCode.ACCESS_TOKEN_REQUIRED)));
    }
}
