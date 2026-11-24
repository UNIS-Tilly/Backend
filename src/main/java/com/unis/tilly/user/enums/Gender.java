package com.unis.tilly.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 성별 (선택 항목, D-96). 목록 확정: 남성·여성·기타 (D-142)
@Getter
@RequiredArgsConstructor
public enum Gender {
    MALE("남성"),
    FEMALE("여성"),
    OTHER("기타");

    private final String label;
}
