package com.unis.tilly.auth.service;

// 현재 사용자는 항상 이 인터페이스로 얻는다 (요청 본문·쿼리의 사용자 ID를 믿지 않는다).
// 구현체는 인증 단계(global/security의 JWT 필터 연동 후)에서 추가한다.
public interface CurrentUserProvider {

    Long getCurrentUserId();
}
