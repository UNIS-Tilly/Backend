package com.unis.tilly.auth.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.global.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// 요청 본문·쿼리의 사용자 ID를 믿지 않고, JwtAuthenticationFilter가 SecurityContext에 심어둔 값만 사용한다.
@Component
public class CurrentUserProviderImpl implements CurrentUserProvider {

    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new CustomException(ErrorCode.ACCESS_TOKEN_REQUIRED);
        }
        return user.userId();
    }
}
