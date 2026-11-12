package com.unis.tilly.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 아직 역할(role) 기반 인가는 쓰지 않지만, 나중에 추가되어도 같은 ApiResponse 형식으로 응답하도록 미리 둔다.
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    // Boot 4는 Jackson 2 ObjectMapper 빈을 기본 제공하지 않아 직접 둔다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ErrorCode.FORBIDDEN)));
    }
}
