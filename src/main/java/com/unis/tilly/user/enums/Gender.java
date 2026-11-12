package com.unis.tilly.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 성별 (선택 항목, D-96). 값과 표시 문구는 [임시] — 기획 확정 시 교체
// TODO(NOTICE-043): 기획 확정 목록으로 교체
@Getter
@RequiredArgsConstructor
public enum Gender {
    MALE("남성"),
    FEMALE("여성"),
    OTHER("기타");

    private final String label;
}
