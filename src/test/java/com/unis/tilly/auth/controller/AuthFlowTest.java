package com.unis.tilly.auth.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.auth.service.CurrentUserProvider;
import com.unis.tilly.global.common.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// local 프로파일에서 dev-login(NOTICE-030/D-91) → 보호된 엔드포인트 접근 → 로그아웃 무효화 → 재발급 회전까지
// 실제 JWT·Redis 경로로 끝까지 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(AuthFlowTest.ProtectedTestController.class)
@Testcontainers
class AuthFlowTest {

    private static final Pattern REFRESH_COOKIE_PATTERN = Pattern.compile("refreshToken=([^;]*)");

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    // Boot 4의 테스트 슬라이스는 Jackson ObjectMapper 빈을 자동 주입하지 않아 직접 생성한다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dev_login으로_발급받은_Access_토큰으로_보호된_엔드포인트에_접근할_수_있다() throws Exception {
        String accessToken = devLoginAndGetAccessToken();

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    void 토큰_없이_보호된_엔드포인트에_접근하면_401_ACCESS_TOKEN_REQUIRED() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_TOKEN_REQUIRED"));
    }

    @Test
    void 로그아웃하면_그_Access_토큰은_바로_무효화된다() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/dev-login")).andReturn();
        String accessToken = extractAccessToken(loginResult);
        String refreshCookie = extractRefreshCookie(loginResult);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(new Cookie("refreshToken", refreshCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void 재발급은_커스텀_헤더가_없으면_거부된다() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/dev-login")).andReturn();
        String refreshCookie = extractRefreshCookie(loginResult);

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new Cookie("refreshToken", refreshCookie)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 재발급은_쿠키와_커스텀_헤더가_있으면_새_Access_토큰을_주고_Refresh를_회전한다() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/dev-login")).andReturn();
        String refreshCookie = extractRefreshCookie(loginResult);

        mockMvc.perform(post("/api/auth/reissue")
                        .header("X-Tilly-Client", "web")
                        .cookie(new Cookie("refreshToken", refreshCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());

        // 회전되었으니 같은 Refresh로 다시 재발급하면 거부된다
        mockMvc.perform(post("/api/auth/reissue")
                        .header("X-Tilly-Client", "web")
                        .cookie(new Cookie("refreshToken", refreshCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    private String devLoginAndGetAccessToken() throws Exception {
        return extractAccessToken(mockMvc.perform(post("/api/auth/dev-login")).andReturn());
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        return (String) data.get("accessToken");
    }

    private String extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        Matcher matcher = REFRESH_COOKIE_PATTERN.matcher(setCookie == null ? "" : setCookie);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Set-Cookie에서 refreshToken을 찾지 못함: " + setCookie);
    }

    @RestController
    static class ProtectedTestController {

        private final CurrentUserProvider currentUserProvider;

        ProtectedTestController(CurrentUserProvider currentUserProvider) {
            this.currentUserProvider = currentUserProvider;
        }

        @GetMapping("/test/protected")
        public ApiResponse<Map<String, Object>> protectedEndpoint() {
            return ApiResponse.success("ok", Map.of("userId", currentUserProvider.getCurrentUserId()));
        }
    }
}
