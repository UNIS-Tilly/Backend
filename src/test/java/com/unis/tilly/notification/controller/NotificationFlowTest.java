package com.unis.tilly.notification.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unis.tilly.auth.client.GoogleOidcClient;
import com.unis.tilly.notification.entity.Notification;
import com.unis.tilly.notification.enums.NotificationTargetType;
import com.unis.tilly.notification.enums.NotificationType;
import com.unis.tilly.notification.repository.NotificationRepository;
import com.unis.tilly.notification.service.NotificationService;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.repository.TaskRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 13장 알림 API(D-152)를 실제 가입된 사용자 + MySQL로 끝까지 확인한다. 외부 호출 없음.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NotificationFlowTest {

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
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private GoogleOidcClient googleOidcClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Notification save(Long userId, NotificationType type, String key) {
        return notificationRepository.save(new Notification(userId, type, "제목", "본문 " + key,
                NotificationTargetType.RECORD, null, LocalDate.of(2026, 11, 28), key));
    }

    @Test
    void 알림이_없으면_빈_목록과_0을_돌려준다() throws Exception {
        Session me = register();

        mockMvc.perform(get("/api/notifications").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void 인증_없이는_401이다() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void 최신순으로_내려주고_읽음_처리와_모두_읽음이_개수에_반영된다() throws Exception {
        Session me = register();
        Notification first = save(me.userId(), NotificationType.RECORD_REMINDER, "k1");
        Notification second = save(me.userId(), NotificationType.INSIGHT_PUBLISHED, "k2");

        mockMvc.perform(get("/api/notifications").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(first.getId()))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].type").value("INSIGHT_PUBLISHED"))
                .andExpect(jsonPath("$.data.items[0].target.type").value("RECORD"))
                .andExpect(jsonPath("$.data.items[0].target.id").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].target.date").value("2026-11-28"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));

        // 하나 읽음 (두 번 눌러도 200, 개수 그대로)
        mockMvc.perform(patch("/api/notifications/" + first.getId() + "/read").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
        mockMvc.perform(patch("/api/notifications/" + first.getId() + "/read").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
        mockMvc.perform(get("/api/notifications").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.items[1].read").value(true));

        // 모두 읽음
        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void 다른_사용자의_알림은_보이지_않고_읽음_처리도_404이다() throws Exception {
        Session me = register();
        Session other = register();
        Notification theirs = save(other.userId(), NotificationType.RECORD_REMINDER, "k1");

        mockMvc.perform(get("/api/notifications").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(patch("/api/notifications/" + theirs.getId() + "/read").header("Authorization", me.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notifications/999999999/read").header("Authorization", me.bearer()))
                .andExpect(status().isNotFound());
        // 내가 모두 읽음을 눌러도 남의 알림은 그대로
        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", me.bearer()))
                .andExpect(status().isOk());
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(other.userId())).isEqualTo(1);
    }

    @Test
    void 페이지_크기는_1에서_50까지이고_다음_페이지가_있으면_hasNext가_true이다() throws Exception {
        Session me = register();
        save(me.userId(), NotificationType.RECORD_REMINDER, "k1");
        save(me.userId(), NotificationType.RECORD_REMINDER, "k2");
        save(me.userId(), NotificationType.RECORD_REMINDER, "k3");

        mockMvc.perform(get("/api/notifications?page=0&size=2").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(3));
        mockMvc.perform(get("/api/notifications?page=1&size=2").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(get("/api/notifications?size=51").header("Authorization", me.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notifications?page=-1").header("Authorization", me.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 마감_알림은_실제_할_일로_한_번만_만들어지고_완료한_할_일에는_만들지_않는다() throws Exception {
        Session me = register();
        LocalDate day = LocalDate.of(2026, 11, 28);
        LocalDateTime due = day.atTime(11, 0);
        Task open = taskRepository.save(new Task(me.userId(), "보고서", day, due));
        Task done = taskRepository.save(new Task(me.userId(), "완료한 일", day, due));
        done.complete(day);
        taskRepository.save(done);

        LocalDateTime now = day.atTime(10, 0, 20);
        notificationService.createDueSoonNotifications(now);
        notificationService.createDueSoonNotifications(now.plusMinutes(1));   // 다시 실행해도 중복 없음

        mockMvc.perform(get("/api/notifications").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("TASK_DUE_SOON"))
                .andExpect(jsonPath("$.data.items[0].body").value("'보고서'가 오늘 11:00에 마감이에요."))
                .andExpect(jsonPath("$.data.items[0].target.type").value("TASK"))
                .andExpect(jsonPath("$.data.items[0].target.id").value(open.getId()))
                .andExpect(jsonPath("$.data.items[0].target.date").value("2026-11-28"));
    }

    private Session register() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        given(googleOidcClient.exchangeCodeForUserInfo(sub)).willReturn(
                new GoogleOidcClient.GoogleUserInfo(sub, "user-" + UUID.randomUUID() + "@example.com", true));
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
}
