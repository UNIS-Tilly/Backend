package com.unis.tilly.notification.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import com.unis.tilly.notification.dto.UnreadCountResponse;
import com.unis.tilly.notification.entity.Notification;
import com.unis.tilly.notification.enums.NotificationTargetType;
import com.unis.tilly.notification.enums.NotificationType;
import com.unis.tilly.notification.repository.NotificationRepository;
import com.unis.tilly.record.enums.RecordStatus;
import com.unis.tilly.record.repository.DailyRecordRepository;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.repository.TaskRepository;
import com.unis.tilly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 알림 생성 규칙·읽음 처리 (D-152, API_SPEC 13장)
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 10, 0, 30);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private DailyRecordRepository recordRepository;
    @Mock
    private WeeklyInsightRepository insightRepository;
    @Mock
    private UserService userService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, taskRepository, recordRepository,
                insightRepository, userService, Clock.system(SEOUL));
    }

    private Task task(long id, long userId, String title, LocalDateTime due) {
        Task task = new Task(userId, title, TODAY, due);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---- 마감 알림 ----

    @Test
    void 마감_1시간_안의_미완료_할_일에_알림을_만든다() {
        LocalDateTime due = TODAY.atTime(10, 45);
        given(taskRepository.findDueBetweenNotCompleted(TODAY.atTime(10, 0), TODAY.atTime(11, 0)))
                .willReturn(List.of(task(77L, 1L, "보고서", due)));

        int created = service.createDueSoonNotifications(NOW);

        assertThat(created).isEqualTo(1);
        Notification n = saved();
        assertThat(n.getUserId()).isEqualTo(1L);
        assertThat(n.getType()).isEqualTo(NotificationType.TASK_DUE_SOON);
        assertThat(n.getTitle()).isEqualTo("마감이 곧 다가와요");
        assertThat(n.getBody()).isEqualTo("'보고서'가 오늘 10:45에 마감이에요.");
        assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.TASK);
        assertThat(n.getTargetId()).isEqualTo(77L);
        assertThat(n.getTargetDate()).isEqualTo(TODAY);
        assertThat(n.getDedupeKey()).isEqualTo("TASK_DUE_SOON:77:202611281045");
    }

    @Test
    void 이미_만든_마감_알림은_다시_만들지_않는다() {
        given(taskRepository.findDueBetweenNotCompleted(any(), any()))
                .willReturn(List.of(task(77L, 1L, "보고서", TODAY.atTime(10, 45))));
        given(notificationRepository.existsByUserIdAndDedupeKey(1L, "TASK_DUE_SOON:77:202611281045")).willReturn(true);

        assertThat(service.createDueSoonNotifications(NOW)).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 마감_시각을_고치면_새_키로_한_번_더_만든다() {
        given(taskRepository.findDueBetweenNotCompleted(any(), any()))
                .willReturn(List.of(task(77L, 1L, "보고서", TODAY.atTime(10, 50))));

        assertThat(service.createDueSoonNotifications(NOW)).isEqualTo(1);
        assertThat(saved().getDedupeKey()).isEqualTo("TASK_DUE_SOON:77:202611281050");
    }

    @Test
    void 동시에_만들다_유니크_키에_걸리면_조용히_건너뛴다() {
        given(taskRepository.findDueBetweenNotCompleted(any(), any()))
                .willReturn(List.of(task(77L, 1L, "보고서", TODAY.atTime(10, 45))));
        given(notificationRepository.save(any())).willThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.createDueSoonNotifications(NOW)).isZero();
    }

    @Test
    void 한_사용자의_생성이_실패해도_다른_할_일은_계속_만든다() {
        given(taskRepository.findDueBetweenNotCompleted(any(), any())).willReturn(List.of(
                task(1L, 1L, "가", TODAY.atTime(10, 30)), task(2L, 2L, "나", TODAY.atTime(10, 31))));
        given(notificationRepository.save(any())).willThrow(new IllegalStateException("db"))
                .willAnswer(i -> i.getArgument(0));

        assertThat(service.createDueSoonNotifications(NOW)).isEqualTo(1);
    }

    // ---- 기록 알림 ----

    @Test
    void 퇴근_5분_전에_오늘_기록이_완료가_아니면_알림을_만든다() {
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(10, 5))).willReturn(List.of(1L));
        given(recordRepository.existsByUserIdAndDateAndStatus(1L, TODAY, RecordStatus.COMPLETED)).willReturn(false);

        assertThat(service.createRecordReminders(NOW)).isEqualTo(1);

        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.RECORD_REMINDER);
        assertThat(n.getTitle()).isEqualTo("오늘 기록을 아직 남기지 않았어요");
        assertThat(n.getBody()).isEqualTo("퇴근 전 5분만 투자해 오늘의 경험을 기록해 볼까요?");
        assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.RECORD);
        assertThat(n.getTargetId()).isNull();
        assertThat(n.getTargetDate()).isEqualTo(TODAY);
        assertThat(n.getDedupeKey()).isEqualTo("RECORD_REMINDER:2026-11-28");
    }

    @Test
    void 오늘_기록을_이미_완료했으면_만들지_않는다() {
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(10, 5))).willReturn(List.of(1L));
        given(recordRepository.existsByUserIdAndDateAndStatus(1L, TODAY, RecordStatus.COMPLETED)).willReturn(true);

        assertThat(service.createRecordReminders(NOW)).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 자정을_넘기는_퇴근_시간도_실행한_날_기준으로_만든다() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 28, 23, 58, 0);
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(0, 3))).willReturn(List.of(1L));

        service.createRecordReminders(now);

        assertThat(saved().getDedupeKey()).isEqualTo("RECORD_REMINDER:2026-11-28");
    }

    // ---- 인사이트 알림 ----

    @Test
    void 인사이트가_발행되면_알림을_만든다() {
        WeeklyInsight insight = new WeeklyInsight(1L, LocalDate.of(2026, 11, 9), NOW);
        ReflectionTestUtils.setField(insight, "id", 10L);
        given(insightRepository.findById(10L)).willReturn(Optional.of(insight));

        service.notifyInsightPublished(10L);

        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.INSIGHT_PUBLISHED);
        assertThat(n.getTitle()).isEqualTo("주간 인사이트가 도착했어요");
        assertThat(n.getBody()).isEqualTo("11월 2주차 분석 결과를 확인해 보세요.");
        assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.INSIGHT);
        assertThat(n.getTargetId()).isEqualTo(10L);
        assertThat(n.getTargetDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(n.getDedupeKey()).isEqualTo("INSIGHT_PUBLISHED:10");
    }

    @Test
    void 인사이트_알림_생성이_실패해도_예외를_밖으로_던지지_않는다() {
        given(insightRepository.findById(10L)).willThrow(new IllegalStateException("db"));

        assertThatCode(() -> service.notifyInsightPublished(10L)).doesNotThrowAnyException();
    }

    // ---- 조회·읽음 ----

    @Test
    void 목록의_page와_size가_범위를_벗어나면_400이다() {
        assertThatThrownBy(() -> service.getList(1L, -1, 20)).isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThatThrownBy(() -> service.getList(1L, 0, 51)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.getList(1L, 0, 0)).isInstanceOf(CustomException.class);
    }

    @Test
    void 없는_알림이나_남의_알림은_404이다() {
        given(notificationRepository.findByIdAndUserId(5L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(1L, 5L)).isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    void 읽음_처리하면_남은_안_읽은_개수를_돌려준다() {
        Notification n = new Notification(1L, NotificationType.RECORD_REMINDER, "t", "b", null, null, null, "k");
        given(notificationRepository.findByIdAndUserId(5L, 1L)).willReturn(Optional.of(n));
        given(notificationRepository.countByUserIdAndReadAtIsNull(1L)).willReturn(2L);

        UnreadCountResponse response = service.markRead(1L, 5L);

        assertThat(n.isRead()).isTrue();
        assertThat(response.unreadCount()).isEqualTo(2L);
    }

    @Test
    void 이미_읽은_알림은_읽은_시각을_바꾸지_않는다() {
        Notification n = new Notification(1L, NotificationType.RECORD_REMINDER, "t", "b", null, null, null, "k");
        n.markRead(NOW.minusDays(1));

        n.markRead(NOW);

        assertThat(n.getReadAt()).isEqualTo(NOW.minusDays(1));
    }

    @Test
    void 만료된_알림을_삭제한다() {
        given(notificationRepository.deleteOlderThan(NOW.minusDays(30))).willReturn(3);

        assertThat(service.deleteExpired(NOW)).isEqualTo(3);
        verify(notificationRepository).deleteOlderThan(eq(NOW.minusDays(30)));
    }
}
