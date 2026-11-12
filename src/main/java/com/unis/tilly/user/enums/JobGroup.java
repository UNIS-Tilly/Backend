package com.unis.tilly.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 직군 (재직 직군·희망 직군 공용, D-96, D-99). 목록은 [임시]
// TODO(NOTICE-043): 기획 확정 목록으로 교체 (코드 값이 DB에 저장되므로 출시 전 확정 필요)
@Getter
@RequiredArgsConstructor
public enum JobGroup {
    DEVELOPMENT("개발"),
    DESIGN("디자인"),
    PLANNING("기획·PM"),
    MARKETING("마케팅"),
    DATA("데이터 분석");

    private final String label;
}
