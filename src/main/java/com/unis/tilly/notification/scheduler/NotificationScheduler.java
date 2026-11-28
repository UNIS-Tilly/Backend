package com.unis.tilly.notification.scheduler;

import com.unis.tilly.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

// 13.5 알림 생성 스케줄러 (D-152). 자동 질문(6.6)과 같은 방식: 매분 실행, 다중 인스턴스에서는 ShedLock으로 한 인스턴스만 실행.
// - TASK_DUE_SOON: 마감이 1시간 안으로 들어온 미완료 할 일 (구간 조회라 잠깐 꺼졌다 켜져도 보충됨)
// - RECORD_REMINDER: 퇴근 시간이 "지금 + 5분"인 사용자 중 오늘 기록이 퇴근 완료가 아닌 경우 (그 1분에만)
// - 매일 03:30 30일 지난 알림 삭제
// notification.scheduler.enabled=false로 끌 수 있다(기본 켜짐)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "notification-generate", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
    public void run() {
        runAt(LocalDateTime.now(clock));
    }

    // 테스트용으로 시각을 받는다. 한쪽이 실패해도 다른 쪽은 실행한다
    void runAt(LocalDateTime now) {
        try {
            notificationService.createDueSoonNotifications(now);
        } catch (RuntimeException e) {
            log.warn("마감 알림 생성 실패: reason={}", e.getClass().getSimpleName());
        }
        try {
            notificationService.createRecordReminders(now);
        } catch (RuntimeException e) {
            log.warn("기록 알림 생성 실패: reason={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "notification-cleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void cleanUp() {
        try {
            int deleted = notificationService.deleteExpired(LocalDateTime.now(clock));
            log.info("보관 기간이 지난 알림을 삭제함: count={}", deleted);
        } catch (RuntimeException e) {
            log.warn("알림 정리 실패: reason={}", e.getClass().getSimpleName());
        }
    }
}
