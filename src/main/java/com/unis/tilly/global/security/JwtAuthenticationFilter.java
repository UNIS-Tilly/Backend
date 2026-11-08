package com.unis.tilly.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.global.common.response.ApiResponse;
import com.unis.tilly.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

// Authorization: Bearer <accessToken> 을 검증해 SecurityContext에 AuthenticatedUser를 심는다.
// 토큰이 없으면 그냥 다음 필터로 넘긴다 (인증이 필요한 요청인지는 SecurityConfig의 authorizeHttpRequests가 판단 → 없으면 EntryPoint가 401 처리).
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    // Boot 4는 Jackson 2 ObjectMapper 빈을 기본 제공하지 않아 직접 둔다 (단순 JSON 직렬화만 필요).
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtProvider.parseAccessToken(token);
            String jti = claims.getId();

            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti))) {
                writeError(response, ErrorCode.INVALID_ACCESS_TOKEN);
                return;
            }

            Long userId = Long.valueOf(claims.getSubject());
            Instant expiresAt = claims.getExpiration().toInstant();
            AuthenticatedUser principal = new AuthenticatedUser(userId, jti, expiresAt);

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            writeError(response, ErrorCode.EXPIRED_ACCESS_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, ErrorCode.INVALID_ACCESS_TOKEN);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode)));
    }
}
