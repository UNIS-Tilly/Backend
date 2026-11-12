package com.unis.tilly.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 직군 (재직 직군·희망 직군 공용, D-96, D-99). 기획 확정 목록 (2026-11-12)
// 코드 값은 DB에 저장되므로 출시 후 이름을 바꾸려면 데이터 변환 마이그레이션이 필요하다.
@Getter
@RequiredArgsConstructor
public enum JobGroup {
    MARKETING("마케팅"),
    IT_DEVELOPMENT("IT/개발"),
    HR("인사/HR"),
    MANUFACTURING("생산/제조"),
    SALES("영업/판매"),
    STRATEGY_PLANNING("전략기획"),
    FINANCE_ACCOUNTING("재무/회계"),
    PR_ADVERTISING("홍보/광고"),
    PURCHASING("구매/조달"),
    QUALITY_CONTROL("품질관리"),
    PRODUCTION_MGMT("생산관리"),
    RND("R&D"),
    DESIGN("디자인");

    private final String label;
}
