package com.unis.tilly.global.common.response;

// 도메인별 구역으로 나눠 관리한다. 문구는 명세에 없는 잠정 문구이므로 각 단위 구현 시 자유롭게 수정 가능.
public record SuccessMessage(String message) {

    // Default (조회 등 공통)
    public static final SuccessMessage OK = new SuccessMessage("요청이 성공했습니다.");

    // Auth
    public static final SuccessMessage LOGOUT = new SuccessMessage("로그아웃되었습니다.");

    // User
    public static final SuccessMessage PROFILE_UPDATED = new SuccessMessage("회원 정보가 수정되었습니다.");
    public static final SuccessMessage WORK_END_TIME_UPDATED = new SuccessMessage("퇴근 시간이 변경되었습니다.");
    public static final SuccessMessage WITHDRAWAL_COMPLETED = new SuccessMessage("탈퇴가 완료되었습니다.");

    // Task
    public static final SuccessMessage TASK_CREATED = new SuccessMessage("할 일이 추가되었습니다.");
    public static final SuccessMessage TASK_UPDATED = new SuccessMessage("할 일이 수정되었습니다.");
    public static final SuccessMessage TASK_DELETED = new SuccessMessage("할 일이 삭제되었습니다.");

    // Record
    public static final SuccessMessage RECORD_DRAFT_SAVED = new SuccessMessage("임시저장되었습니다.");
    public static final SuccessMessage RECORD_COMPLETED = new SuccessMessage("하루 기록이 완료되었습니다.");
    public static final SuccessMessage QUESTIONS_GENERATED = new SuccessMessage("확장 질문을 불러왔습니다.");

    // Insight
    public static final SuccessMessage INSIGHT_ANALYSIS_REQUESTED = new SuccessMessage("분석을 요청했습니다.");
    public static final SuccessMessage INSIGHT_ACTION_REFLECTED = new SuccessMessage("다음 주 월요일 체크리스트에 반영되었습니다.");
}
