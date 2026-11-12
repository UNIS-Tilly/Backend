package com.unis.tilly.task.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.auth.client.GoogleOidcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 5.1~5.5, HM-02~08, RC-03 (D-102~D-106)를 실제 가입된 사용자 + MySQL 기준으로 끝까지 확인한다.
// "오늘"은 테스트용 Clock으로 고정·이동한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TaskFlowTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private GoogleOidcClient googleOidcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setToday() {
        clock.setToday(LocalDate.of(2026, 10, 21));
    }

    // ---- 목록 (HM-02, HM-03, RC-02) ----

    @Test
    void HM02_완료와_미완료를_나눠_개수와_함께_돌려준다() throws Exception {
        String token = registerAndGetAccessToken();
        Long done = createTask(token, "끝낸 일", "2026-10-21", null);
        createTask(token, "남은 일", "2026-10-21", null);
        patchTask(token, done, "{\"completed\":true}").andExpect(status().isOk());

        getTasks(token, "2026-10-21")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-10-21"))
                .andExpect(jsonPath("$.data.completedCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.completed[0].title").value("끝낸 일"))
                .andExpect(jsonPath("$.data.completed[0].completed").value(true))
                .andExpect(jsonPath("$.data.completed[0].completedDate").value("2026-10-21"))
                .andExpect(jsonPath("$.data.incomplete[0].title").value("남은 일"))
                .andExpect(jsonPath("$.data.incomplete[0].memo").doesNotExist());
    }

    @Test
    void HM02_날짜를_생략하면_오늘_목록이다() throws Exception {
        String token = registerAndGetAccessToken();
        createTask(token, "오늘 일", "2026-10-21", null);

        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-10-21"))
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    void HM02_배정일이_지난_미완료는_이월되고_마감이_지나면_overdueDays를_준다() throws Exception {
        String token = registerAndGetAccessToken();
        createTask(token, "지연된 일", "2026-10-15", "2026-10-17");
        createTask(token, "미래 일", "2026-10-25", null);

        getTasks(token, "2026-10-21")
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.incomplete[0].title").value("지연된 일"))
                .andExpect(jsonPath("$.data.incomplete[0].overdueDays").value(4));
    }

    @Test
    void HM02_배정일이_없는_할_일은_생성일부터_계속_보인다() throws Exception {
        String token = registerAndGetAccessToken();
        createTask(token, "날짜 없는 일", null, null);

        getTasks(token, "2026-10-20").andExpect(jsonPath("$.data.totalCount").value(0));
        getTasks(token, "2026-10-21").andExpect(jsonPath("$.data.incomplete[0].title").value("날짜 없는 일"));
        getTasks(token, "2026-11-30").andExpect(jsonPath("$.data.incomplete[0].date").doesNotExist());
    }

    @Test
    void HM02_과거_날짜는_그날_기준으로_다시_계산한다() throws Exception {
        String token = registerAndGetAccessToken();
        Long taskId = createTask(token, "이틀 걸린 일", "2026-10-20", null);
        patchTask(token, taskId, "{\"completed\":true}").andExpect(status().isOk()); // 10/21 완료

        // 10/20에는 아직 미완료였다
        getTasks(token, "2026-10-20")
                .andExpect(jsonPath("$.data.completedCount").value(0))
                .andExpect(jsonPath("$.data.incomplete[0].id").value(taskId))
                .andExpect(jsonPath("$.data.incomplete[0].completed").value(false))
                .andExpect(jsonPath("$.data.incomplete[0].completedDate").doesNotExist());
        // 10/21에는 완료 목록에만 있고, 그 뒤로는 보이지 않는다
        getTasks(token, "2026-10-21").andExpect(jsonPath("$.data.completed[0].id").value(taskId));
        getTasks(token, "2026-10-22").andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void HM02_토큰_없이_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/tasks").param("date", "2026-10-21"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void HM02_날짜_형식이_잘못되면_400() throws Exception {
        String token = registerAndGetAccessToken();

        getTasks(token, "2026/10/21").andExpect(status().isBadRequest());
    }

    // ---- 추가 (HM-05) ----

    @Test
    void HM05_배정일과_마감일을_넣어_추가한다() throws Exception {
        String token = registerAndGetAccessToken();

        postTask(token, Map.of("title", "고객 문의 정리", "date", "2026-10-21", "dueDate", "2026-10-23"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("고객 문의 정리"))
                .andExpect(jsonPath("$.data.date").value("2026-10-21"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-10-23"))
                .andExpect(jsonPath("$.data.completed").value(false));
    }

    @Test
    void HM05_제목이_비어있으면_400() throws Exception {
        String token = registerAndGetAccessToken();

        postTask(token, Map.of("title", "   ")).andExpect(status().isBadRequest());
    }

    @Test
    void HM05_그날_목록이_완료_포함_20개면_TASK_LIMIT_EXCEEDED() throws Exception {
        String token = registerAndGetAccessToken();
        Long first = createTask(token, "할일0", "2026-10-21", null);
        for (int i = 1; i < 20; i++) {
            createTask(token, "할일" + i, "2026-10-21", null);
        }
        patchTask(token, first, "{\"completed\":true}").andExpect(status().isOk()); // 완료해도 그날 목록에 남는다

        postTask(token, Map.of("title", "21번째", "date", "2026-10-21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TASK_LIMIT_EXCEEDED"));
        // 배정일 없는 할 일도 오늘 목록에 보이므로 막힌다
        postTask(token, Map.of("title", "날짜 없는 일"))
                .andExpect(jsonPath("$.errorCode").value("TASK_LIMIT_EXCEEDED"));
        // 다른 날은 추가된다
        postTask(token, Map.of("title", "다음 날 일", "date", "2026-10-22")).andExpect(status().isCreated());
    }

    // ---- 수정 / 완료 토글 (HM-04, HM-07, RC-03) ----

    @Test
    void HM04_완료_토글과_제목_수정을_한다() throws Exception {
        String token = registerAndGetAccessToken();
        Long taskId = createTask(token, "원래 제목", "2026-10-21", null);

        patchTask(token, taskId, "{\"completed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.completedDate").value("2026-10-21"))
                .andExpect(jsonPath("$.data.title").value("원래 제목"));

        patchTask(token, taskId, "{\"completed\":false,\"title\":\"바꾼 제목\"}")
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.completedDate").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("바꾼 제목"));
    }

    @Test
    void HM07_생략한_필드는_유지되고_null을_보내면_해제된다() throws Exception {
        String token = registerAndGetAccessToken();
        Long taskId = createTask(token, "제목", "2026-10-21", "2026-10-23");

        patchTask(token, taskId, "{\"title\":\"새 제목\"}")
                .andExpect(jsonPath("$.data.date").value("2026-10-21"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-10-23"));

        patchTask(token, taskId, "{\"dueDate\":null}")
                .andExpect(jsonPath("$.data.date").value("2026-10-21"))
                .andExpect(jsonPath("$.data.dueDate").doesNotExist());

        patchTask(token, taskId, "{\"date\":null,\"dueDate\":\"2026-10-30\"}")
                .andExpect(jsonPath("$.data.date").doesNotExist())
                .andExpect(jsonPath("$.data.dueDate").value("2026-10-30"));
    }

    @Test
    void HM07_배정일을_가득_찬_날로_옮기면_TASK_LIMIT_EXCEEDED() throws Exception {
        String token = registerAndGetAccessToken();
        // 먼저 만든다: 10/22의 미완료 20개는 10/25에도 이월되어 보이므로, 나중에 만들면 10/25 추가 자체가 막힌다
        Long taskId = createTask(token, "옮길 일", "2026-10-25", null);
        for (int i = 0; i < 20; i++) {
            createTask(token, "할일" + i, "2026-10-22", null);
        }
        postTask(token, Map.of("title", "이월로 가득 찬 날", "date", "2026-10-25"))
                .andExpect(jsonPath("$.errorCode").value("TASK_LIMIT_EXCEEDED"));

        patchTask(token, taskId, "{\"date\":\"2026-10-22\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TASK_LIMIT_EXCEEDED"));
    }

    @Test
    void HM07_존재하지_않는_할_일을_수정하면_404() throws Exception {
        String token = registerAndGetAccessToken();

        patchTask(token, 999999L, "{\"title\":\"바꾼 제목\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"));
    }

    @Test
    void HM07_다른_사용자의_할_일은_수정할_수_없다() throws Exception {
        String ownerToken = registerAndGetAccessToken();
        Long taskId = createTask(ownerToken, "원래 제목", "2026-10-21", null);
        String otherToken = registerAndGetAccessToken();

        patchTask(otherToken, taskId, "{\"title\":\"가로채기\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"));
        getTasks(otherToken, "2026-10-21").andExpect(jsonPath("$.data.totalCount").value(0));
    }

    // ---- 삭제 (HM-08) ----

    @Test
    void HM08_삭제하면_목록에서_없어지고_다른_사용자는_삭제할_수_없다() throws Exception {
        String token = registerAndGetAccessToken();
        Long taskId = createTask(token, "지울 일", "2026-10-21", null);
        String otherToken = registerAndGetAccessToken();

        mockMvc.perform(delete("/api/tasks/" + taskId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tasks/" + taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        getTasks(token, "2026-10-21").andExpect(jsonPath("$.data.totalCount").value(0));
    }

    // ---- helpers ----

    private ResultActions getTasks(String token, String date) throws Exception {
        return mockMvc.perform(get("/api/tasks").param("date", date).header("Authorization", "Bearer " + token));
    }

    private ResultActions postTask(String token, Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/api/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions patchTask(String token, Long taskId, String json) throws Exception {
        return mockMvc.perform(patch("/api/tasks/" + taskId)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json));
    }

    private Long createTask(String token, String title, String date, String dueDate) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", title);
        body.put("date", date);
        body.put("dueDate", dueDate);
        MvcResult result = postTask(token, body).andExpect(status().isCreated()).andReturn();
        Map<String, Object> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {
                });
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        return ((Number) data.get("id")).longValue();
    }

    private String registerAndGetAccessToken() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        String email = "user-" + UUID.randomUUID() + "@example.com";
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
                                "nickname", "닉네임"))))
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

    // 테스트에서 "오늘"을 옮길 수 있는 Clock (Asia/Seoul)
    static class MutableClock extends Clock {
        private volatile Instant instant = Instant.now();

        void setToday(LocalDate today) {
            this.instant = today.atTime(10, 0).atZone(SEOUL).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
