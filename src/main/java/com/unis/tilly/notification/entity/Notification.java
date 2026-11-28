package com.unis.tilly.notification.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import com.unis.tilly.notification.enums.NotificationTargetType;
import com.unis.tilly.notification.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 13.0 알림 한 건. 문구는 만들 때 저장하고 바꾸지 않는다. readAt이 null이면 안 읽음
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    // (user_id, dedupe_key) 유니크: 같은 알림을 두 번 만들지 않는다
    @Column(name = "dedupe_key", nullable = false, length = 100)
    private String dedupeKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public Notification(Long userId, NotificationType type, String title, String body,
                        NotificationTargetType targetType, Long targetId, LocalDate targetDate, String dedupeKey) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetDate = targetDate;
        this.dedupeKey = dedupeKey;
    }

    public boolean isRead() {
        return readAt != null;
    }

    // 이미 읽은 알림이면 최초 읽은 시각을 유지한다 (멱등)
    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
