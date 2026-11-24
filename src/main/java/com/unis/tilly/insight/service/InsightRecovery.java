package com.unis.tilly.insight.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 서버가 시작될 때 GENERATING으로 남은 인사이트를 FAILED로 정리한다 (사용자 결정 2026-11-24).
// 실행 중이던 생성 작업은 서버와 함께 사라졌고, 남겨 두면 그 주 기록이 계속 잠긴다.
// 주의: 서버 인스턴스를 여러 대로 늘리면 다른 인스턴스에서 실행 중인 생성도 실패 처리될 수 있다(그 경우 사용자가 재요청한다).
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightRecovery {

    private final InsightStore store;

    @EventListener(ApplicationReadyEvent.class)
    public void failLeftoverGenerating() {
        int count = store.failAllGenerating();
        if (count > 0) {
            log.warn("서버 시작 시 생성 중으로 남은 인사이트를 실패 처리함: {}건", count);
        }
    }
}
