package com.unis.tilly.record.scheduler;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.record.service.RecordQuestionService;
import com.unis.tilly.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 6.6 자동 질문 생성 스케줄러 (D-48, D-138): 퇴근 10분 전, 그 1분만, 실패는 건너뜀
@ExtendWith(MockitoExtension.class)
class AutoQuestionSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private UserService userService;
    @Mock
    private RecordQuestionService recordQuestionService;

    private AutoQuestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AutoQuestionScheduler(userService, recordQuestionService, Clock.system(SEOUL));
    }

    @Test
    void RC06_퇴근_시간이_지금_10분_뒤인_사용자의_오늘_기록으로_생성한다() {
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(18, 0))).willReturn(List.of(1L, 2L));

        scheduler.runAt(LocalDateTime.of(2026, 10, 21, 17, 50, 0));

        LocalDate today = LocalDate.of(2026, 10, 21);
        verify(recordQuestionService).generateAutomatically(1L, today);
        verify(recordQuestionService).generateAutomatically(2L, today);
    }

    @Test
    void RC06_초는_버리고_분_단위로_맞춘다() {
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(18, 0))).willReturn(List.of());

        scheduler.runAt(LocalDateTime.of(2026, 10, 21, 17, 50, 59));

        verify(userService).findUserIdsByWorkEndTime(LocalTime.of(18, 0));
    }

    @Test
    void RC06_자정을_넘기는_퇴근_시간도_실행한_날의_기록으로_만든다() {
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(0, 5))).willReturn(List.of(7L));

        scheduler.runAt(LocalDateTime.of(2026, 10, 21, 23, 55, 0));

        verify(recordQuestionService).generateAutomatically(7L, LocalDate.of(2026, 10, 21));
    }

    @Test
    void RC06_대상이_없으면_아무것도_하지_않는다() {
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(9, 10))).willReturn(List.of());

        scheduler.runAt(LocalDateTime.of(2026, 10, 21, 9, 0, 0));

        verify(recordQuestionService, never()).generateAutomatically(anyLong(), any());
    }

    @Test
    void RC06_한_사용자가_실패해도_나머지_사용자는_계속_생성한다() {
        given(userService.findUserIdsByWorkEndTime(LocalTime.of(18, 0))).willReturn(List.of(1L, 2L, 3L));
        LocalDate today = LocalDate.of(2026, 10, 21);
        given(recordQuestionService.generateAutomatically(1L, today))
                .willThrow(new CustomException(ErrorCode.AI_TIMEOUT));
        given(recordQuestionService.generateAutomatically(2L, today))
                .willThrow(new IllegalStateException("예상치 못한 오류"));

        scheduler.runAt(LocalDateTime.of(2026, 10, 21, 17, 50, 0));

        verify(recordQuestionService, times(3)).generateAutomatically(anyLong(), any());
        verify(recordQuestionService).generateAutomatically(3L, today);
    }
}
