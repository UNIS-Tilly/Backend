package com.unis.tilly.notification.scheduler;

import com.unis.tilly.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// 13.5 알림 스케줄러: 한쪽이 실패해도 다른 쪽은 실행한다
@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 10, 0, 0);

    @Mock
    private NotificationService notificationService;

    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationScheduler(notificationService, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 마감_알림과_기록_알림을_모두_실행한다() {
        scheduler.runAt(NOW);

        verify(notificationService).createDueSoonNotifications(NOW);
        verify(notificationService).createRecordReminders(NOW);
    }

    @Test
    void 마감_알림이_실패해도_기록_알림은_실행한다() {
        given(notificationService.createDueSoonNotifications(NOW)).willThrow(new IllegalStateException("db"));

        assertThatCode(() -> scheduler.runAt(NOW)).doesNotThrowAnyException();
        verify(notificationService).createRecordReminders(NOW);
    }

    @Test
    void 정리가_실패해도_예외를_던지지_않는다() {
        given(notificationService.deleteExpired(org.mockito.ArgumentMatchers.any())).willThrow(new IllegalStateException("db"));

        assertThatCode(() -> scheduler.cleanUp()).doesNotThrowAnyException();
    }
}
