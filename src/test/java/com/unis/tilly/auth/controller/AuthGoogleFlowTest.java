package com.unis.tilly.auth.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.auth.client.GoogleOidcClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 구글 서버는 실제로 호출하지 않는다 — GoogleOidcClient만 Mock으로 바꾸고, 그 뒤(가입/로그인/토큰)는 전부
// 실제 경로(MySQL, Redis, JWT)로 끝까지 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthGoogleFlowTest {

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

    @MockitoBean
    private GoogleOidcClient googleOidcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 처음_로그인하면_가입_토큰을_받고_가입하면_실제_토큰을_발급받는다() throws Exception {
        given(googleOidcClient.exchangeCodeForUserInfo("new-user-code"))
                .willReturn(new GoogleOidcClient.GoogleUserInfo("new-sub-1", "newbie@example.com", true));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json")
                        .content("{\"code\":\"new-user-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.data.email").value("newbie@example.com"))
                .andReturn();

        String signupToken = readField(loginResult, "signupToken");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "signupToken", signupToken,
                                "nickname", "틸리"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.userId").exists())
                .andReturn();

        assertThat(registerResult.getResponse().getHeader("Set-Cookie")).contains("refreshToken=");
    }

    @Test
    void 가입된_사용자가_다시_로그인하면_바로_토큰을_받는다() throws Exception {
        given(googleOidcClient.exchangeCodeForUserInfo("existing-user-code"))
                .willReturn(new GoogleOidcClient.GoogleUserInfo("existing-sub-1", "repeat@example.com", true));

        MvcResult firstLogin = mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json")
                        .content("{\"code\":\"existing-user-code\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String signupToken = readField(firstLogin, "signupToken");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "signupToken", signupToken,
                                "nickname", "다시로그인"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json")
                        .content("{\"code\":\"existing-user-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void code가_비어있으면_400_BAD_REQUEST() throws Exception {
        mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json")
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("unchecked")
    private String readField(MvcResult result, String field) throws Exception {
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get(field);
    }
}
