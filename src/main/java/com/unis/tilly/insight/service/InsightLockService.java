package com.unis.tilly.insight.service;

import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 기록 잠금 판정 (API_SPEC 1.5, D-130, D-137): 그 날짜가 속한 주의 인사이트가 GENERATING(만료 전)이거나 PUBLISHED이면 잠긴다.
// task·record가 이 서비스만 호출하고, 이 서비스는 insight 저장소만 읽는다(task·record에 의존하지 않아 순환이 없다).
@Service
@RequiredArgsConstructor
public class InsightLockService {

    // 생성 요청 뒤 이 시간이 지나도 GENERATING이면 실패로 본다 (사용자 결정 2026-11-24: 10분)
    public static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(10);

    private final WeeklyInsightRepository insightRepository;
    private final Clock clock;

    public boolean isLocked(Long userId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        return insightRepository.findByUserIdAndWeekStart(userId, WeekRules.weekStart(date))
                .map(insight -> insight.locksWeek(now, GENERATION_TIMEOUT))
                .orElse(false);
    }
}
