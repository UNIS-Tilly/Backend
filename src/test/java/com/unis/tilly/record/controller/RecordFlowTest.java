package com.unis.tilly.record.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.record.entity.DailyRecord;
import com.unis.tilly.record.entity.RecordQuestion;
import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.repository.DailyRecordRepository;
import com.unis.tilly.record.repository.RecordQuestionRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 6.1~6.4, 7.1 (RC-05, RC-09, RC-10, RC-12, RC-13, HM-10, HM-11, AR-01, AR-02, AR-04)를
// 실제 가입된 사용자 + MySQL로 끝까지 확인한다. 질문 생성(6.5)은 AI 연동 단계라 질문은 저장소로 직접 넣는다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RecordFlowTest {

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
    @Autowired
    private DailyRecordRepository recordRepository;
    @Autowired
    private RecordQuestionRepository questionRepository;

    @MockitoBean
    private GoogleOidcClient googleOidcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setToday() {
        clock.setToday(LocalDate.of(2026, 10, 21));
    }

    // ---- 6.3 임시저장 / 6.1 요약 ----

    @Test
    void RC12_수동_임시저장하면_DRAFT가_되고_메모가_할_일_목록과_요약에_반영된다() throws Exception {
        Session me = register();
        Long taskId = createTask(me, "보고서", "2026-10-21");

        saveDraft(me, "2026-10-21", true, List.of(memo(taskId, "구조를 바꿨다")), List.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.savedAt").value("2026-10-21T10:00:00"));

        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.incomplete[0].memo").value("구조를 바꿨다"));
        summary(me, "2026-10-21")
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.memoCount").value(1))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    void RC12_자동저장은_메모만_저장하고_상태는_NOT_STARTED로_남는다() throws Exception {
        Session me = register();
        Long taskId = createTask(me, "보고서", "2026-10-21");

        saveDraft(me, "2026-10-21", false, List.of(memo(taskId, "자동저장 메모")), List.of())
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"));

        summary(me, "2026-10-21")
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.memoCount").value(1));
        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.incomplete[0].memo").value("자동저장 메모"));
    }

    @Test
    void RC12_보내지_않은_메모는_지우고_그날_보이지_않는_할_일의_메모는_건드리지_않는다() throws Exception {
        Session me = register();
        // 10/20에 완료한 할 일 → 10/21에는 보이지 않는다
        clock.setToday(LocalDate.of(2026, 10, 20));
        Long doneYesterday = createTask(me, "어제 끝낸 일", "2026-10-20");
        saveDraft(me, "2026-10-20", true, List.of(memo(doneYesterday, "어제 메모")), List.of());
        patch(me, doneYesterday, "{\"completed\":true}");
        clock.setToday(LocalDate.of(2026, 10, 21));
        Long today = createTask(me, "오늘 일", "2026-10-21");
        saveDraft(me, "2026-10-21", true, List.of(memo(today, "오늘 메모")), List.of());

        saveDraft(me, "2026-10-21", true, List.of(), List.of()).andExpect(status().isOk());

        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.incomplete[0].memo").doesNotExist());
        getTasks(me, "2026-10-20").andExpect(jsonPath("$.data.completed[0].memo").value("어제 메모"));
    }

    @Test
    void RC12_그날_보이지_않거나_다른_사용자의_할_일에_메모하면_TASK_NOT_FOUND() throws Exception {
        Session owner = register();
        Long ownerTask = createTask(owner, "남의 일", "2026-10-21");
        Session me = register();
        Long futureTask = createTask(me, "미래 일", "2026-10-25");

        saveDraft(me, "2026-10-21", true, List.of(memo(ownerTask, "가로채기")), List.of())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"));
        saveDraft(me, "2026-10-21", true, List.of(memo(futureTask, "미리")), List.of())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"));

        getTasks(owner, "2026-10-21").andExpect(jsonPath("$.data.incomplete[0].memo").doesNotExist());
    }

    @Test
    void RC12_형식_오류는_400() throws Exception {
        Session me = register();
        Long taskId = createTask(me, "보고서", "2026-10-21");

        saveDraft(me, "2026-10-21", true, List.of(memo(taskId, "가".repeat(501))), List.of())
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/records/2026-10-21/draft").header("Authorization", me.bearer())
                        .contentType("application/json").content("{\"taskMemos\":[]}"))
                .andExpect(status().isBadRequest());
        saveDraft(me, "2026-13-01", true, List.of(), List.of()).andExpect(status().isBadRequest());
    }

    @Test
    void HM10_토큰_없이_요약을_조회하면_401() throws Exception {
        mockMvc.perform(get("/api/records/2026-10-21/summary")).andExpect(status().isUnauthorized());
    }

    // ---- 6.2 하루 질문 / 답변 ----

    @Test
    void RC10_질문을_순서대로_조회하고_답변을_전체_교체한다() throws Exception {
        Session me = register();
        List<Long> ids = givenQuestions(me, LocalDate.of(2026, 10, 21));

        questions(me, "2026-10-21")
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.questions[0].order").value(1))
                .andExpect(jsonPath("$.data.questions[0].answer").doesNotExist());

        saveDraft(me, "2026-10-21", false, List.of(), List.of(
                answer(ids.get(0), "첫 답", false), answer(ids.get(1), "둘째 답", false), answer(ids.get(2), null, true)))
                .andExpect(status().isOk());
        questions(me, "2026-10-21")
                .andExpect(jsonPath("$.data.questions[0].answer").value("첫 답"))
                .andExpect(jsonPath("$.data.questions[2].skipped").value(true));

        // 1번만 보내면 2·3번은 비워진다
        saveDraft(me, "2026-10-21", false, List.of(), List.of(answer(ids.get(0), "첫 답 수정", false)));
        questions(me, "2026-10-21")
                .andExpect(jsonPath("$.data.questions[0].answer").value("첫 답 수정"))
                .andExpect(jsonPath("$.data.questions[1].answer").doesNotExist())
                .andExpect(jsonPath("$.data.questions[2].skipped").value(false));
    }

    @Test
    void RC09_질문이_없는_날은_빈_배열이고_다른_사용자의_질문에는_답할_수_없다() throws Exception {
        Session owner = register();
        List<Long> ownerQuestions = givenQuestions(owner, LocalDate.of(2026, 10, 21));
        Session me = register();

        questions(me, "2026-10-21").andExpect(jsonPath("$.data.questions.length()").value(0));
        saveDraft(me, "2026-10-21", true, List.of(), List.of(answer(ownerQuestions.get(0), "가로채기", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUESTION_NOT_FOUND"));
        // 다른 날짜의 질문 ID도 거절
        saveDraft(owner, "2026-10-22", true, List.of(), List.of(answer(ownerQuestions.get(0), "날짜 다름", false)))
                .andExpect(jsonPath("$.errorCode").value("QUESTION_NOT_FOUND"));
    }

    // ---- 6.4 완료 + 성장 레벨 ----

    @Test
    void RC13_빈_기록도_완료되고_완료한_날이_늘면_레벨이_오른다() throws Exception {
        Session me = register();

        complete(me, "2026-10-21")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-10-21T10:00:00"))
                .andExpect(jsonPath("$.data.level").value(2));   // 1일

        clock.setToday(LocalDate.of(2026, 10, 22));
        complete(me, "2026-10-21") // 다시 완료: 최초 시각·레벨 유지
                .andExpect(jsonPath("$.data.completedAt").value("2026-10-21T10:00:00"))
                .andExpect(jsonPath("$.data.level").value(2));
        complete(me, "2026-10-22").andExpect(jsonPath("$.data.level").value(2)); // 2일
        complete(me, "2026-10-23").andExpect(jsonPath("$.data.level").value(3)); // 3일

        mockMvc.perform(get("/api/users/me").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.level").value(3));
        summary(me, "2026-10-21")
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-10-21T10:00:00"));
    }

    @Test
    void RC13_완료할_때_보낸_메모와_답변도_저장되고_완료_후_임시저장해도_COMPLETED를_유지한다() throws Exception {
        Session me = register();
        Long taskId = createTask(me, "보고서", "2026-10-21");

        mockMvc.perform(post("/api/records/2026-10-21/complete").header("Authorization", me.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("taskMemos", List.of(memo(taskId, "퇴근 메모"))))))
                .andExpect(status().isOk());
        saveDraft(me, "2026-10-21", true, List.of(memo(taskId, "퇴근 후 수정")), List.of())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        getTasks(me, "2026-10-21").andExpect(jsonPath("$.data.incomplete[0].memo").value("퇴근 후 수정"));
    }

    // ---- 7.1 아카이브 ----

    @Test
    void AR01_그달_모든_날짜에_기록_유무를_돌려준다() throws Exception {
        Session me = register();
        Long taskId = createTask(me, "보고서", "2026-10-01");
        saveDraft(me, "2026-10-19", true, List.of(memo(taskId, "임시저장")), List.of());       // DRAFT
        complete(me, "2026-10-21");                                                         // COMPLETED
        saveDraft(me, "2026-10-20", false, List.of(memo(taskId, "자동저장만")), List.of());     // 기록 없음

        mockMvc.perform(get("/api/archive/calendar").param("year", "2026").param("month", "10")
                        .header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(10))
                .andExpect(jsonPath("$.data.days.length()").value(31))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.data.days[0].hasRecord").value(false))
                .andExpect(jsonPath("$.data.days[18].hasRecord").value(true))
                .andExpect(jsonPath("$.data.days[19].hasRecord").value(false))
                .andExpect(jsonPath("$.data.days[20].hasRecord").value(true));
    }

    @Test
    void AR01_월이_범위_밖이거나_빠지면_400이고_다른_사용자의_기록은_보이지_않는다() throws Exception {
        Session owner = register();
        complete(owner, "2026-10-21");
        Session me = register();

        mockMvc.perform(get("/api/archive/calendar").param("year", "2026").param("month", "13")
                        .header("Authorization", me.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/archive/calendar").param("year", "2026").header("Authorization", me.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        mockMvc.perform(get("/api/archive/calendar").param("year", "2026").param("month", "10")
                        .header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.days[20].hasRecord").value(false));
    }

    // ---- helpers ----

    private List<Long> givenQuestions(Session session, LocalDate date) {
        DailyRecord record = recordRepository.save(new DailyRecord(session.userId(), date, RecordStatus.NOT_STARTED));
        return List.of(1, 2, 3).stream()
                .map(order -> questionRepository.save(new RecordQuestion(record.getId(), order, "질문" + order)).getId())
                .toList();
    }

    private Map<String, Object> memo(Long taskId, String memo) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("taskId", taskId);
        map.put("memo", memo);
        return map;
    }

    private Map<String, Object> answer(Long questionId, String answer, boolean skipped) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("questionId", questionId);
        map.put("answer", answer);
        map.put("skipped", skipped);
        return map;
    }

    private ResultActions saveDraft(Session session, String date, boolean manual,
                                    List<Map<String, Object>> memos, List<Map<String, Object>> answers) throws Exception {
        return mockMvc.perform(put("/api/records/" + date + "/draft").header("Authorization", session.bearer())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("manual", manual, "taskMemos", memos, "answers", answers))));
    }

    private ResultActions complete(Session session, String date) throws Exception {
        return mockMvc.perform(post("/api/records/" + date + "/complete").header("Authorization", session.bearer())
                .contentType("application/json").content("{}"));
    }

    private ResultActions summary(Session session, String date) throws Exception {
        return mockMvc.perform(get("/api/records/" + date + "/summary").header("Authorization", session.bearer()));
    }

    private ResultActions questions(Session session, String date) throws Exception {
        return mockMvc.perform(get("/api/records/" + date + "/questions").header("Authorization", session.bearer()));
    }

    private ResultActions getTasks(Session session, String date) throws Exception {
        return mockMvc.perform(get("/api/tasks").param("date", date).header("Authorization", session.bearer()));
    }

    private void patch(Session session, Long taskId, String json) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/tasks/" + taskId).header("Authorization", session.bearer())
                        .contentType("application/json").content(json))
                .andExpect(status().isOk());
    }

    private Long createTask(Session session, String title, String date) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks").header("Authorization", session.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", title, "date", date))))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) data(result).get("id")).longValue();
    }

    private Session register() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        given(googleOidcClient.exchangeCodeForUserInfo(sub))
                .willReturn(new GoogleOidcClient.GoogleUserInfo(sub, "user-" + UUID.randomUUID() + "@example.com", true));
        MvcResult login = mockMvc.perform(post("/api/auth/login/google")
                        .contentType("application/json").content("{\"code\":\"" + sub + "\"}"))
                .andReturn();
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "signupToken", data(login).get("signupToken"), "nickname", "닉네임"))))
                .andReturn();
        Map<String, Object> body = data(registered);
        return new Session((String) body.get("accessToken"), ((Number) body.get("userId")).longValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
        return (Map<String, Object>) body.get("data");
    }

    private record Session(String accessToken, Long userId) {
        String bearer() {
            return "Bearer " + accessToken;
        }
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
