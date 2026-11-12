package com.unis.tilly.user.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.repository.UserCompetencyRepository;
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
import org.springframework.test.web.servlet.ResultActions;
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

// 내 정보 조회(4.1) · 프로필 수정(4.2) · 프로필 선택지(4.3) · 퇴근 시간 설정(4.4) · 탈퇴(4.7)를 실제 가입 흐름으로 만든
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

    @Autowired
    private UserCompetencyRepository userCompetencyRepository;

    @MockitoBean
    private GoogleOidcClient googleOidcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 가장 최근에 가입시킨 사용자의 구글 sub (탈퇴 확인용)
    private String lastSub;

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
    void ST01_가입_직후에는_레벨_1이고_역량이_비어있다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "틸리");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.competencies.length()").value(0))
                .andExpect(jsonPath("$.data.gender").doesNotExist())
                .andExpect(jsonPath("$.data.targetJobGroup").doesNotExist());
    }

    @Test
    void 프로필을_수정하면_내_정보에_반영된다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "원래닉네임");

        putProfile(accessToken, """
                {"nickname":"바꾼닉네임","gender":"FEMALE","birthYear":2001,
                 "currentJobGroup":"IT_DEVELOPMENT","targetJobGroup":"DESIGN",
                 "competencies":["DSN_COMMUNICATION","PARTNERSHIP","DSN_TOOL"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("바꾼닉네임"))
                .andExpect(jsonPath("$.data.competencies[0]").value("PARTNERSHIP"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.nickname").value("바꾼닉네임"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.birthYear").value(2001))
                .andExpect(jsonPath("$.data.currentJobGroup").value("IT_DEVELOPMENT"))
                .andExpect(jsonPath("$.data.targetJobGroup").value("DESIGN"))
                .andExpect(jsonPath("$.data.competencies.length()").value(3))
                .andExpect(jsonPath("$.data.level").value(1));
    }

    @Test
    void 프로필은_전체_교체되어_같은_역량을_다시_보내도_된다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");
        putProfile(accessToken, """
                {"nickname":"닉네임","targetJobGroup":"DESIGN",
                 "competencies":["PARTNERSHIP","DSN_COMMUNICATION","DSN_TOOL"]}""")
                .andExpect(status().isOk());

        // 같은 역량(PARTNERSHIP)을 유지하면서 희망 직군 해제 + 다른 항목 생략 → 해제
        putProfile(accessToken, "{\"nickname\":\"닉네임\",\"competencies\":[\"PARTNERSHIP\",\"RESILIENCE\"]}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.targetJobGroup").doesNotExist())
                .andExpect(jsonPath("$.data.competencies.length()").value(2))
                .andExpect(jsonPath("$.data.competencies[0]").value("PARTNERSHIP"))
                .andExpect(jsonPath("$.data.competencies[1]").value("RESILIENCE"));
    }

    @Test
    void 역량_규칙을_어기면_INVALID_COMPETENCY_SELECTION이고_저장되지_않는다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");

        // 희망 직군 역량 1개
        putProfile(accessToken, """
                {"nickname":"바꾼닉네임","targetJobGroup":"DESIGN","competencies":["PARTNERSHIP","DSN_COMMUNICATION"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_COMPETENCY_SELECTION"));
        // 공통 역량 없음
        putProfile(accessToken, "{\"nickname\":\"바꾼닉네임\",\"competencies\":[]}")
                .andExpect(jsonPath("$.errorCode").value("INVALID_COMPETENCY_SELECTION"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.nickname").value("닉네임"));
    }

    @Test
    void 프로필_형식_오류는_400_BAD_REQUEST() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");

        putProfile(accessToken, "{\"nickname\":\"a\",\"competencies\":[\"PARTNERSHIP\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        putProfile(accessToken, "{\"nickname\":\"닉네임\",\"competencies\":[\"NO_SUCH_COMPETENCY\"]}")
                .andExpect(status().isBadRequest());
        putProfile(accessToken, "{\"nickname\":\"닉네임\",\"targetJobGroup\":\"NO_SUCH\",\"competencies\":[\"PARTNERSHIP\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 닉네임_전용_수정_API는_삭제되었다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");

        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"nickname\":\"바꾼닉네임\"}"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.nickname").value("닉네임"));
    }

    @Test
    void 프로필_선택지를_조회한다() throws Exception {
        String accessToken = registerAndGetAccessToken(uniqueEmail(), "닉네임");

        mockMvc.perform(get("/api/meta/profile-options").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.genders.length()").value(3))
                .andExpect(jsonPath("$.data.genders[0].code").value("MALE"))
                .andExpect(jsonPath("$.data.genders[0].label").value("남성"))
                .andExpect(jsonPath("$.data.jobGroups.length()").value(13))
                .andExpect(jsonPath("$.data.competencies.length()").value(77))
                .andExpect(jsonPath("$.data.competencies[0].type").value("COMMON"))
                .andExpect(jsonPath("$.data.competencies[0].jobGroup").doesNotExist())
                .andExpect(jsonPath("$.data.competencies[8].jobGroup").doesNotExist())
                .andExpect(jsonPath("$.data.competencies[9].code").value("MKT_ANALYSIS"))
                .andExpect(jsonPath("$.data.competencies[9].jobGroup").value("MARKETING"))
                .andExpect(jsonPath("$.data.competencies[0].label").value("파트너십"))
                .andExpect(jsonPath("$.data.competencies[0].description").isNotEmpty())
                .andExpect(jsonPath("$.data.selectionRules.commonMin").value(1))
                .andExpect(jsonPath("$.data.selectionRules.jobMin").value(2));
    }

    @Test
    void 토큰_없이_선택지를_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/meta/profile-options"))
                .andExpect(status().isUnauthorized());
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
        String sub = lastSub;
        putProfile(accessToken, "{\"nickname\":\"닉네임\",\"competencies\":[\"PARTNERSHIP\"]}")
                .andExpect(status().isOk());
        Long userId = userRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, sub).orElseThrow().getId();
        assertThat(userCompetencyRepository.findAllByUserId(userId)).hasSize(1);

        mockMvc.perform(post("/api/users/me/withdrawal")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"confirmEmail\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, sub)).isEmpty();
        // 역량은 ON DELETE CASCADE로 함께 삭제된다
        assertThat(userCompetencyRepository.findAllByUserId(userId)).isEmpty();

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

    private ResultActions putProfile(String accessToken, String json) throws Exception {
        return mockMvc.perform(put("/api/users/me/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .content(json));
    }

    private String registerAndGetAccessToken(String email, String nickname) throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        lastSub = sub;
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
