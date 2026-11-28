package com.unis.tilly.notification.dto;

import com.unis.tilly.notification.entity.Notification;
import com.unis.tilly.notification.enums.NotificationTargetType;
import com.unis.tilly.notification.enums.NotificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// 13.1 GET /notifications. createdAt은 "2026-11-28T10:00:00"(초까지, 오프셋 없음, 서울 시간), 상대 시간 표시는 프론트가 한다
public record NotificationResponse(long unreadCount, List<Item> items, int page, int size, long totalElements,
                                   boolean hasNext) {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    public record Item(Long id, NotificationType type, String title, String body, boolean read, String createdAt,
                       Target target) {

        public static Item from(Notification notification) {
            return new Item(notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getBody(), notification.isRead(), format(notification.getCreatedAt()),
                    Target.from(notification));
        }
    }

    // 이동 대상이 없으면 Item.target 자체가 null
    public record Target(NotificationTargetType type, Long id, String date) {

        static Target from(Notification notification) {
            if (notification.getTargetType() == null) {
                return null;
            }
            LocalDate date = notification.getTargetDate();
            return new Target(notification.getTargetType(), notification.getTargetId(),
                    date == null ? null : date.toString());
        }
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME.format(dateTime);
    }
}
