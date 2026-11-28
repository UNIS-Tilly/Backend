package com.unis.tilly.notification.repository;

import com.unis.tilly.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 13.1 목록 (정렬은 Pageable로: 최신순)
    Page<Notification> findAllByUserId(Long userId, Pageable pageable);

    // 13.2 안 읽은 개수
    long countByUserIdAndReadAtIsNull(Long userId);

    // 13.3 타 사용자 알림은 없는 것과 같게 취급
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // 13.5 중복 방지
    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    // 13.4 모두 읽음
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now, n.updatedAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 13.0 보관 기간이 지난 알림 삭제
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
