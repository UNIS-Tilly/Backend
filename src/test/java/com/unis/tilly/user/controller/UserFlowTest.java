package com.unis.tilly.user.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.user.repository.UserRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 내 정보 조회(4.1) · 닉네임 수정(4.2) · 퇴근 시간 설정(4.3) · 탈퇴(4.5)를 실제 가입 흐름으로 만든
// 사용자에 대해 끝까지 확인한다. 구글 서버는 GoogleOidcClient만 Mock으로 대체한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserFlowTest {

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

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private GoogleOidcClient googleOidcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 내_정보를_조회한다() throws Exception {
        String email = uniqueEmail();
        String accessToken = registerAndGetAccessToken(email, "틸리");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("틸리"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.workEndTime").doesNotExist());
    }

    @Test
    void 토큰_없이_내_정보를_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 닉네임을_수정한다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "원래닉네임");

        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"nickname\":\"바꾼닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("바꾼닉네임"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.nickname").value("바꾼닉네임"));
    }

    @Test
    void 닉네임이_1자면_400() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");

        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"nickname\":\"a\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 퇴근_시간을_설정하고_해제한다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");

        mockMvc.perform(put("/api/users/me/work-end-time")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"workEndTime\":\"18:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workEndTime").value("18:00"));

        mockMvc.perform(put("/api/users/me/work-end-time")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"workEndTime\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workEndTime").doesNotExist());
    }

    @Test
    void 퇴근_시간_형식이_잘못되면_400() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");

        mockMvc.perform(put("/api/users/me/work-end-time")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"workEndTime\":\"25:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이메일이_일치하면_탈퇴되고_토큰도_즉시_무효화된다() throws Exception {
        String email = uniqueEmail();
        String accessToken = registerAndGetAccessToken(email, "닉네임");

        mockMvc.perform(post("/api/users/me/withdrawal")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"confirmEmail\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findByProviderAndProviderUserId(
                com.unis.tilly.user.enums.SocialProvider.GOOGLE, email)).isEmpty();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void 이메일이_일치하지_않으면_탈퇴가_거부된다() throws Exception {
        String email = uniqueEmail();
        String accessToken = registerAndGetAccessToken(email, "닉네임");

        mockMvc.perform(post("/api/users/me/withdrawal")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"confirmEmail\":\"other@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_MISMATCH"));
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String registerAndGetAccessToken(String email, String nickname) throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        given(googleOidcClient.exchangeCodeForUserInfo(sub))
                .willReturn(new GoogleOidcClient.GoogleUserInfo(sub, email, true));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json")
                        .content("{\"code\":\"" + sub + "\"}"))
                .andReturn();
        String signupToken = readField(loginResult, "signupToken");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "signupToken", signupToken,
                                "nickname", nickname))))
                .andReturn();
        return readField(registerResult, "accessToken");
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
