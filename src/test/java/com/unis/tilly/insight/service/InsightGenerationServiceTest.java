package com.unis.tilly.insight.service;

import com.unis.tilly.ai.client.WeeklyInsightGenerator;
import com.unis.tilly.ai.dto.WeeklyInsightResult;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 비동기 생성 실행 (사용자 결정 2026-11-24): 성공하면 발행, 어떤 실패든 FAILED로 돌려 잠금을 푼다
@ExtendWith(MockitoExtension.class)
class InsightGenerationServiceTest {

    private static final InsightInputAssembler.Assembled ASSEMBLED =
            new InsightInputAssembler.Assembled(null, null, "{}", "[]");
    private static final WeeklyInsightResult RESULT = new WeeklyInsightResult("제목", "소제목", List.of(), List.of());

    @Mock
    private WeeklyInsightGenerator generator;
    @Mock
    private InsightStore store;
    @Mock
    private NotificationService notificationService;

    private InsightGenerationService service(Executor executor) {
        return new InsightGenerationService(generator, store, notificationService, executor);
    }

    @Test
    void IN03_생성에_성공하면_결과를_발행한다() {
        given(generator.generate(any())).willReturn(RESULT);
        given(store.publish(10L, RESULT, ASSEMBLED)).willReturn(true);

        service(Runnable::run).submit(10L, ASSEMBLED);

        verify(store).publish(10L, RESULT, ASSEMBLED);
        verify(store, never()).markFailed(any());
        verify(notificationService).notifyInsightPublished(10L);   // D-152
    }

    @Test
    void IN03_AI_오류가_나면_FAILED로_돌린다() {
        given(generator.generate(any())).willThrow(new CustomException(ErrorCode.AI_TIMEOUT));

        service(Runnable::run).submit(10L, ASSEMBLED);

        verify(store).markFailed(10L);
        verify(store, never()).publish(any(), any(), any());
        verify(notificationService, never()).notifyInsightPublished(any());
    }

    @Test
    void IN03_예상치_못한_오류나_저장_실패도_FAILED로_돌린다() {
        given(generator.generate(any())).willReturn(RESULT);
        given(store.publish(10L, RESULT, ASSEMBLED)).willThrow(new IllegalStateException("db"));

        service(Runnable::run).submit(10L, ASSEMBLED);

        verify(store).markFailed(10L);
    }

    @Test
    void IN03_그_사이_만료되어_저장되지_않았으면_추가로_실패_처리하지_않는다() {
        given(generator.generate(any())).willReturn(RESULT);
        given(store.publish(10L, RESULT, ASSEMBLED)).willReturn(false);

        service(Runnable::run).submit(10L, ASSEMBLED);

        verify(store, never()).markFailed(any());
    }

    @Test
    void IN03_작업_큐가_가득_차면_실패_처리하고_오류를_올려_잠금을_남기지_않는다() {
        Executor rejecting = command -> {
            throw new TaskRejectedException("full");
        };

        assertThatThrownBy(() -> service(rejecting).submit(10L, ASSEMBLED))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AI_GENERATION_FAILED));
        verify(store).markFailed(10L);
        verify(generator, never()).generate(any());
    }
}
