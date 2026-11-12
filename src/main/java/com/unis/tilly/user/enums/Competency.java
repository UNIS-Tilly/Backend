package com.unis.tilly.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.unis.tilly.user.enums.CompetencyType.COMMON;
import static com.unis.tilly.user.enums.CompetencyType.JOB;

// 얻고 싶은 역량 (D-98, D-99). 공통 6개 + 직군별 4개×5. 목록은 [임시]
// TODO(NOTICE-043): 기획 확정 목록으로 교체 (코드 값이 DB에 저장되므로 출시 전 확정 필요)
@Getter
@RequiredArgsConstructor
public enum Competency {
    // 공통 역량
    COMMUNICATION("의사소통", COMMON, null),
    COLLABORATION("협업", COMMON, null),
    PROBLEM_SOLVING("문제 해결", COMMON, null),
    TIME_MANAGEMENT("시간 관리", COMMON, null),
    LEARNING_AGILITY("학습·적응력", COMMON, null),
    DOCUMENTATION("문서화·보고", COMMON, null),

    // 개발
    DEV_CODE_QUALITY("코드 품질", JOB, JobGroup.DEVELOPMENT),
    DEV_DEBUGGING("디버깅", JOB, JobGroup.DEVELOPMENT),
    DEV_DESIGN("설계·아키텍처", JOB, JobGroup.DEVELOPMENT),
    DEV_TOOLING("버전 관리·협업 도구", JOB, JobGroup.DEVELOPMENT),

    // 디자인
    DSN_VISUAL("시각 디자인", JOB, JobGroup.DESIGN),
    DSN_UX("사용자 경험 설계", JOB, JobGroup.DESIGN),
    DSN_PROTOTYPING("프로토타이핑", JOB, JobGroup.DESIGN),
    DSN_FEEDBACK("피드백 반영", JOB, JobGroup.DESIGN),

    // 기획·PM
    PLN_REQUIREMENTS("요구사항 정리", JOB, JobGroup.PLANNING),
    PLN_PRIORITIZATION("우선순위 판단", JOB, JobGroup.PLANNING),
    PLN_STAKEHOLDER("이해관계자 조율", JOB, JobGroup.PLANNING),
    PLN_DATA_DRIVEN("지표 기반 의사결정", JOB, JobGroup.PLANNING),

    // 마케팅
    MKT_CONTENT("콘텐츠 기획", JOB, JobGroup.MARKETING),
    MKT_ANALYTICS("성과 분석", JOB, JobGroup.MARKETING),
    MKT_CAMPAIGN("캠페인 운영", JOB, JobGroup.MARKETING),
    MKT_CUSTOMER("고객 이해", JOB, JobGroup.MARKETING),

    // 데이터 분석
    DAT_SQL("데이터 추출·SQL", JOB, JobGroup.DATA),
    DAT_ANALYSIS("분석·해석", JOB, JobGroup.DATA),
    DAT_VISUALIZATION("시각화", JOB, JobGroup.DATA),
    DAT_QUALITY("데이터 품질 관리", JOB, JobGroup.DATA);

    // D-98 선택 규칙: 공통 역량 최소 개수, 희망 직군 선택 시 그 직군 역량 최소 개수
    // 최대 선택 개수는 [미확정] — 제한 없음 (TODO(NOTICE-043))
    public static final int COMMON_MIN = 1;
    public static final int JOB_MIN = 2;

    private final String label;
    private final CompetencyType type;
    private final JobGroup jobGroup;
}
