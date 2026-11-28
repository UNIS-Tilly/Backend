package com.unis.tilly.insight.service;

import com.unis.tilly.ai.client.WeeklyInsightGenerator;
import com.unis.tilly.ai.dto.WeeklyInsightResult;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

// 인사이트 생성을 서버 안의 스레드풀에서 비동기로 실행한다 (사용자 결정 2026-11-24).
// AI 호출은 길 수 있어(최대 60초 × 재시도) DB 트랜잭션 밖에서 하고, 결과 저장만 InsightStore의 짧은 트랜잭션으로 한다.
// 성공하면 PUBLISHED, 어떤 실패든 FAILED(재요청 허용, 잠금 해제)이며 로그에는 인사이트 ID와 오류 코드만 남긴다(기록 원문·AI 응답 제외).
@Slf4j
@Service
public class InsightGenerationService {

    private final WeeklyInsightGenerator generator;
    private final InsightStore store;
    private final NotificationService notificationService;
    private final Executor executor;

    public InsightGenerationService(WeeklyInsightGenerator generator, InsightStore store,
                                    NotificationService notificationService,
                                    @Qualifier("insightExecutor") Executor executor) {
        this.generator = generator;
        this.store = store;
        this.notificationService = notificationService;
        this.executor = executor;
    }

    // 대기열이 가득 차 거절되면 실패 처리하고 오류를 돌려준다(잠금이 남지 않도록)
    public void submit(Long insightId, InsightInputAssembler.Assembled assembled) {
        try {
            executor.execute(() -> run(insightId, assembled));
        } catch (TaskRejectedException e) {
            log.warn("인사이트 생성 대기열이 가득 참: insightId={}", insightId);
            store.markFailed(insightId);
            throw new CustomException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    void run(Long insightId, InsightInputAssembler.Assembled assembled) {
        boolean published = false;
        try {
            WeeklyInsightResult result = generator.generate(assembled.input());
            published = store.publish(insightId, result, assembled);
            if (!published) {
                log.warn("인사이트 결과를 저장하지 않음(이미 만료·실패 처리됨): insightId={}", insightId);
            }
        } catch (CustomException e) {
            log.warn("인사이트 생성 실패: insightId={}, errorCode={}", insightId, e.getErrorCode().name());
            store.markFailed(insightId);
        } catch (RuntimeException e) {
            log.warn("인사이트 생성 실패: insightId={}, reason={}", insightId, e.getClass().getSimpleName());
            store.markFailed(insightId);
        }
        // 발행된 뒤의 알림 생성은 위 try 밖에서 한다: 알림이 실패해도 발행된 인사이트를 FAILED로 되돌리지 않는다 (D-152)
        if (published) {
            notificationService.notifyInsightPublished(insightId);
        }
    }
}
