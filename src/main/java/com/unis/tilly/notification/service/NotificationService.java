package com.unis.tilly.notification.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import com.unis.tilly.insight.service.WeekRules;
import com.unis.tilly.notification.dto.NotificationResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 알림 조회·읽음 처리(13.1~13.4)와 생성(13.0, 13.5, D-152).
// 생성 메서드는 트랜잭션 없이 저장 한 건씩 한다: 중복 키 충돌을 잡아도 다른 저장에 영향이 없고, 생성 실패가 원래 작업을 막지 않는다.
// 로그에는 사용자 ID·알림 종류·오류 코드만 남긴다(할 일 제목·기록 원문 제외)
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;           // [제안]
    static final int DUE_SOON_MINUTES = 60;        // 마감 1시간 전 (확정)
    static final int RECORD_REMINDER_LEAD_MINUTES = 5;   // 퇴근 5분 전 (확정)
    static final int RETENTION_DAYS = 30;          // [제안]

    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmm");

    private final NotificationRepository notificationRepository;
    private final TaskRepository taskRepository;
    private final DailyRecordRepository recordRepository;
    private final WeeklyInsightRepository insightRepository;
    private final UserService userService;
    private final Clock clock;

    // 13.1
    @Transactional(readOnly = true)
    public NotificationResponse getList(Long userId, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + "이어야 합니다.");
        }
        Page<Notification> result = notificationRepository.findAllByUserId(userId,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new NotificationResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId),
                result.getContent().stream().map(NotificationResponse.Item::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    // 13.2
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    // 13.3: 이미 읽은 알림이면 그대로 200. 없거나 남의 알림이면 404
    @Transactional
    public UnreadCountResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(LocalDateTime.now(clock));
        notificationRepository.flush();
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    // 13.4
    @Transactional
    public UnreadCountResponse markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now(clock));
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    // ---- 생성 ----

    // TASK_DUE_SOON: 마감이 (지금, 지금+60분] 안이고 미완료인 할 일. 마감 시각을 고치면 새 키로 한 번 더 만들 수 있다.
    // 서버가 잠깐 꺼졌다 켜져도 창 안에 있는 동안은 만들어지도록 "마감 정각"이 아닌 구간으로 조회한다.
    public int createDueSoonNotifications(LocalDateTime now) {
        LocalDateTime from = now.truncatedTo(ChronoUnit.MINUTES);
        List<Task> tasks = taskRepository.findDueBetweenNotCompleted(from, from.plusMinutes(DUE_SOON_MINUTES));
        int created = 0;
        for (Task task : tasks) {
            try {
                if (createIfAbsent(task.getUserId(), NotificationType.TASK_DUE_SOON, NotificationMessages.DUE_SOON_TITLE,
                        NotificationMessages.dueSoonBody(task.getTitle(), task.getDueDate(), now.toLocalDate()),
                        NotificationTargetType.TASK, task.getId(), task.getDate(),
                        "TASK_DUE_SOON:" + task.getId() + ":" + KEY_TIME.format(task.getDueDate()))) {
                    created++;
                }
            } catch (RuntimeException e) {
                log.warn("마감 알림 생성을 건너뜀: userId={}, type={}, reason={}", task.getUserId(),
                        NotificationType.TASK_DUE_SOON, e.getClass().getSimpleName());
            }
        }
        return created;
    }

    // RECORD_REMINDER: 퇴근 시간이 "지금 + 5분"인 사용자 중 오늘 기록이 COMPLETED가 아닌 사용자. 그 1분에만 실행(보충 없음)
    public int createRecordReminders(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime target = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES).plusMinutes(RECORD_REMINDER_LEAD_MINUTES);
        int created = 0;
        for (Long userId : userService.findUserIdsByWorkEndTime(target)) {
            try {
                if (recordRepository.existsByUserIdAndDateAndStatus(userId, today, RecordStatus.COMPLETED)) {
                    continue;
                }
                if (createIfAbsent(userId, NotificationType.RECORD_REMINDER, NotificationMessages.RECORD_TITLE,
                        NotificationMessages.RECORD_BODY, NotificationTargetType.RECORD, null, today,
                        "RECORD_REMINDER:" + today)) {
                    created++;
                }
            } catch (RuntimeException e) {
                log.warn("기록 알림 생성을 건너뜀: userId={}, type={}, reason={}", userId,
                        NotificationType.RECORD_REMINDER, e.getClass().getSimpleName());
            }
        }
        return created;
    }

    // INSIGHT_PUBLISHED: 발행 직후 호출. 어떤 실패도 호출한 쪽(인사이트 발행)에 전달하지 않는다
    public void notifyInsightPublished(Long insightId) {
        try {
            WeeklyInsight insight = insightRepository.findById(insightId).orElse(null);
            if (insight == null) {
                return;
            }
            createIfAbsent(insight.getUserId(), NotificationType.INSIGHT_PUBLISHED, NotificationMessages.INSIGHT_TITLE,
                    NotificationMessages.insightBody(WeekRules.label(insight.getWeekStart())),
                    NotificationTargetType.INSIGHT, insight.getId(), insight.getWeekStart(),
                    "INSIGHT_PUBLISHED:" + insight.getId());
        } catch (RuntimeException e) {
            log.warn("인사이트 알림 생성을 건너뜀: insightId={}, type={}, reason={}", insightId,
                    NotificationType.INSIGHT_PUBLISHED, e.getClass().getSimpleName());
        }
    }

    // 13.0 보관 기간(30일)이 지난 알림 삭제
    @Transactional
    public int deleteExpired(LocalDateTime now) {
        return notificationRepository.deleteOlderThan(now.minusDays(RETENTION_DAYS));
    }

    // 이미 있으면 만들지 않는다(false). 동시에 두 곳에서 만들다 유니크 키에 걸려도 한 건만 남는다
    private boolean createIfAbsent(Long userId, NotificationType type, String title, String body,
                                   NotificationTargetType targetType, Long targetId, LocalDate targetDate,
                                   String dedupeKey) {
        if (notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return false;
        }
        try {
            notificationRepository.save(new Notification(userId, type, title, body, targetType, targetId,
                    targetDate, dedupeKey));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
