package com.unis.tilly.insight.repository;

import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.enums.InsightStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyInsightRepository extends JpaRepository<WeeklyInsight, Long> {

    Optional<WeeklyInsight> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);

    Optional<WeeklyInsight> findByIdAndUserId(Long id, Long userId);

    // 9.4 홈: 가장 최근에 발행된 인사이트
    Optional<WeeklyInsight> findFirstByUserIdAndStatusOrderByWeekStartDesc(Long userId, InsightStatus status);

    // 9.5 목록: 발행된 주만, 정렬·페이지는 Pageable
    Page<WeeklyInsight> findAllByUserIdAndStatus(Long userId, InsightStatus status, Pageable pageable);

    // 서버 시작 시 남은 GENERATING 정리 (사용자 결정 2026-11-24): 실행 중이던 작업은 서버와 함께 사라졌다
    @Modifying
    @Query("update WeeklyInsight w set w.status = com.unis.tilly.insight.enums.InsightStatus.FAILED "
            + "where w.status = com.unis.tilly.insight.enums.InsightStatus.GENERATING")
    int failAllGenerating();
}
