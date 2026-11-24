package com.unis.tilly.record.scheduler;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.record.service.RecordQuestionService;
import com.unis.tilly.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

// 6.6 자동 질문 생성 (D-48, D-138): 매분, 퇴근 시간이 "지금 + 10분"인 사용자의 오늘 기록으로 질문을 만든다.
// - 그 1분에만 실행하고, 서버가 꺼져 있었거나 AI 호출이 실패하면 그 사용자는 건너뛴다(보충 실행 없음). 사용자는 "질문 받기"로 직접 받을 수 있다.
// - 다중 인스턴스에서는 ShedLock(MySQL)으로 한 인스턴스만 실행한다. 락은 1분 미만(최대 55초, 최소 30초)
// - 퇴근 시간 미설정 사용자는 조회되지 않는다. 요일 구분 없이 매일 실행한다.
// - 로그에는 사용자 ID와 오류 코드만 남긴다(기록 원문·AI 응답 제외)
// record.auto-question.enabled=false로 끌 수 있다(기본 켜짐)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "record.auto-question", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AutoQuestionScheduler {

    static final int LEAD_MINUTES = 10;

    private final UserService userService;
    private final RecordQuestionService recordQuestionService;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "auto-question", lockAtMostFor = "PT55S", lockAtLeastFor = "PT30S")
    public void run() {
        runAt(LocalDateTime.now(clock));
    }

    // 테스트용으로 시각을 받는다
    void runAt(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        // 23:55 + 10분 = 00:05처럼 자정을 넘기는 퇴근 시간도 "오늘(실행한 날)" 기록으로 만든다
        LocalTime target = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES).plusMinutes(LEAD_MINUTES);

        for (Long userId : userService.findUserIdsByWorkEndTime(target)) {
            try {
                recordQuestionService.generateAutomatically(userId, date);
            } catch (CustomException e) {
                log.warn("자동 질문 생성을 건너뜀: userId={}, errorCode={}", userId, e.getErrorCode().name());
            } catch (RuntimeException e) {
                log.warn("자동 질문 생성을 건너뜀: userId={}, reason={}", userId, e.getClass().getSimpleName());
            }
        }
    }
}
