package com.unis.tilly.user.enums;

import lombok.Getter;

import static com.unis.tilly.user.enums.CompetencyType.COMMON;
import static com.unis.tilly.user.enums.CompetencyType.JOB;

// 얻고 싶은 역량 (D-98, D-99). 기획 확정 목록 (2026-11-12): 공통 9개 + 직군별 역량.
// description(정의)은 프로필 선택지 API와 인사이트 AI 판단에 쓴다.
// 코드 값은 DB에 저장되므로 출시 후 이름을 바꾸려면 데이터 변환 마이그레이션이 필요하다.
// 여러 직군에 같은 표시명(예: 분석력)이 있어 직군 접두어로 코드를 구분한다.
@Getter
public enum Competency {

    // ---- 공통 역량 ----
    PARTNERSHIP("파트너십", "공통의 목표를 달성하기 위해 다른 사람의 목표달성을 지원함으로써 협력적 분위기 조성"),
    RESILIENCE("회복성", "성과를 유지하고 실패 그 밖의 좌절 스트레스로부터 빠르게 회복"),
    CUSTOMER_FOCUS("고객지향", "고객 요구에 대한 정확한 판단과 신속한 대처 능력으로 고객 만족을 위해 적극적으로 활동하여 가치 창출"),
    VISION("비전제시", "회사 비전 달성을 위해 현재와 미래의 상황을 정의하고 설명하여 지속적인 가치 창출 기회 제시"),
    PERFORMANCE_DRIVE("성과지향", "업무 성과 극대화를 위한 방안 강구 및 업무 수행의 효과성 효율성 추구"),
    CONFLICT_MANAGEMENT("갈등 관리", "이해관계와 관련된 정보를 주도적으로 수집하여 현안의 본질적인 문제 해결"),
    RESPONSIBILITY("책임감", "맡은 바 역할과 임무에 끝까지 최선을 다하여 임무 완수"),
    CHANGE_LEADERSHIP("변화주도", "업무와 관련된 미래 행동에 대한 조치를 취하고 적극적으로 일 실행"),
    SELF_DEVELOPMENT("자기 계발", "자신의 강약점을 파악하고 이를 보완하기 위한 전문적 지식과 기술 습득 노력"),

    // ---- 마케팅 ----
    MKT_ANALYSIS(JobGroup.MARKETING, "분석력", "시장 동향, 고객 행동, 경쟁사 및 캠페인 데이터를 분석해 마케팅 전략과 실행 방향을 도출하는 능력"),
    MKT_CREATIVITY(JobGroup.MARKETING, "창의성", "고객의 관심과 시장 특성을 고려해 차별화된 아이디어와 메시지, 콘텐츠 및 캠페인 방식을 기획하는 능력"),
    MKT_COMMUNICATION(JobGroup.MARKETING, "커뮤니케이션 능력", "고객, 협력사 및 내부 유관 부서와 효과적으로 소통하고, 마케팅 목표와 실행 방향을 명확하게 전달하는 능력"),
    MKT_DIGITAL(JobGroup.MARKETING, "디지털 마케팅 역량", "검색엔진, 소셜 미디어, 온라인 광고 등 디지털 채널을 활용하고 성과 데이터를 분석해 마케팅 활동을 최적화하는 능력"),
    MKT_DECISION_MAKING(JobGroup.MARKETING, "의사결정", "시장 정보와 성과 데이터를 비교해 마케팅 대안을 평가하고, 목표와 자원에 적합한 실행 방안을 선택하는 능력"),
    MKT_CHALLENGE(JobGroup.MARKETING, "도전", "기존의 마케팅 방식에 안주하지 않고 새로운 채널과 아이디어를 실험하며, 결과를 바탕으로 개선을 시도하는 능력"),

    // ---- IT/개발 ----
    DEV_PROBLEM_SOLVING(JobGroup.IT_DEVELOPMENT, "문제 해결 능력", "오류나 시스템 문제의 원인을 체계적으로 파악하고, 적절한 기술적 해결책을 구현해 정상적으로 작동하도록 개선하는 능력"),
    DEV_PROGRAMMING(JobGroup.IT_DEVELOPMENT, "프로그래밍 기술", "프로그래밍 언어와 개발 도구를 활용해 요구사항에 맞는 기능을 구현하고, 코드의 가독성·유지보수성·안정성을 고려하는 능력"),
    DEV_DATA_ANALYSIS(JobGroup.IT_DEVELOPMENT, "데이터 분석", "데이터를 수집·가공·분석해 의미 있는 패턴과 문제를 발견하고, 기술적 판단이나 서비스 개선에 활용하는 능력"),
    DEV_TEAMWORK(JobGroup.IT_DEVELOPMENT, "협업 능력/ 팀워크", "개발 일정과 요구사항을 공유하고, 코드 리뷰·버전 관리·업무 분담 등을 통해 팀원과 함께 안정적으로 결과물을 만드는 능력"),
    DEV_USER_CENTERED(JobGroup.IT_DEVELOPMENT, "사용자 중심 사고", "사용자의 행동과 불편을 파악하고, 이를 기능 요구사항과 사용성 개선으로 연결하는 능력"),
    DEV_CREATIVITY(JobGroup.IT_DEVELOPMENT, "창의력", "기존 기술과 도구를 새로운 방식으로 조합해 기술적 제약이나 사용자 문제를 해결할 대안을 고안하는 능력"),
    DEV_INFORMATION(JobGroup.IT_DEVELOPMENT, "정보 수집&활용", "공식 문서, 기술 자료, 기존 코드와 실험 결과에서 필요한 정보를 찾아 신뢰성을 검토하고 개발 문제 해결에 적용하는 능력"),

    // ---- 인사/HR ----
    HR_COMMUNICATION(JobGroup.HR, "의사소통 능력", "구성원과 지원자의 요구를 정확하게 파악하고, 채용·평가·조직 운영 관련 정보를 명확하고 적절하게 전달하는 능력"),
    HR_ANALYSIS(JobGroup.HR, "분석력", "채용, 평가, 이직률, 조직 만족도 등 인사 데이터를 분석해 인력 운영의 문제를 파악하고 개선 방향을 도출하는 능력"),
    HR_ORGANIZATION(JobGroup.HR, "조직 관리", "조직의 목표와 구성원 특성을 고려해 인력 배치, 역할 분담, 협업 방식 및 조직문화 개선을 지원하는 능력"),
    HR_ETHICS(JobGroup.HR, "윤리의식", "인사 정보의 기밀과 개인정보를 보호하고, 채용과 평가에서 공정성·객관성 및 관련 규정을 준수하는 능력"),
    HR_PROBLEM_SOLVING(JobGroup.HR, "문제해결", "인력 운영이나 조직 내에서 발생한 문제의 원인을 파악하고, 구성원과 조직 모두를 고려한 현실적인 해결책을 마련하는 능력"),
    HR_DRIVE(JobGroup.HR, "추진력", "채용, 교육, 평가 및 조직문화 개선 과제를 구체적인 계획으로 전환하고, 이해관계자와 협력해 실행과 완료까지 이끄는 능력"),
    HR_INTERPERSONAL(JobGroup.HR, "대인관계", "다양한 배경과 입장을 가진 구성원과 신뢰를 형성하고, 상대의 상황을 이해하며 원활한 협력 관계를 유지하는 능력"),
    HR_RELIABILITY(JobGroup.HR, "신뢰성", "약속과 절차를 일관되게 준수하고, 인사 업무를 정확하고 책임감 있게 처리해 구성원과 조직의 신뢰를 확보하는 능력"),
    HR_TEAMWORK(JobGroup.HR, "협업/ 팀워크", "채용, 교육, 평가 및 조직문화 관련 업무에서 유관 부서와 역할을 조율하고 정보를 공유하며, 공동의 목표를 달성하기 위해 협력하는 능력"),
    HR_DATA_ANALYSIS(JobGroup.HR, "HR 데이터분석", "채용 전환율, 이직률, 평가 결과, 교육 효과 및 조직 만족도 등 인사 데이터를 수집·분석해 인력 운영의 문제를 파악하고, 데이터에 근거한 인사 정책과 개선 방안을 도출하는 능력"),

    // ---- 생산/제조 ----
    MFG_QUALITY(JobGroup.MANUFACTURING, "품질 관리", "제품과 공정의 품질 기준을 이해하고, 검사 결과와 불량 데이터를 바탕으로 품질 편차를 파악해 개선하는 능력"),
    MFG_PROCESS_OPTIMIZATION(JobGroup.MANUFACTURING, "프로세스 최적화", "생산 과정의 병목, 낭비 및 비효율을 분석하고 작업 순서와 공정 방식을 개선해 생산성과 효율을 높이는 능력"),
    MFG_SAFETY(JobGroup.MANUFACTURING, "안전 관리", "작업장의 위험 요소를 사전에 파악하고 안전 규정과 작업 절차를 준수하며, 사고 예방과 안전한 작업 환경 조성을 실천하는 능력"),
    MFG_TECHNICAL_SKILL(JobGroup.MANUFACTURING, "기술 숙련도", "생산 장비와 제조 기술의 작동 원리를 이해하고, 작업 조건에 맞게 장비를 정확하고 안정적으로 운용하는 능력"),

    // ---- 영업/판매 ----
    SAL_NEGOTIATION(JobGroup.SALES, "협상력", "고객과 거래처의 요구 및 이해관계를 파악하고, 가격·조건·일정 등을 조율해 상호 수용 가능한 합의와 계약을 이끌어 내는 능력"),
    SAL_CUSTOMER_FOCUS(JobGroup.SALES, "고객 지향성", "고객의 상황과 구매 목적, 요구사항을 파악해 적합한 상품이나 솔루션을 제안하고 만족도와 거래 가치를 높이는 능력"),
    SAL_GOAL_ACHIEVEMENT(JobGroup.SALES, "목표 달성 능력", "매출과 계약 등 영업 목표를 구체적인 활동 계획으로 전환하고, 진행 상황을 점검하며 필요한 조치를 통해 목표 달성을 추진하는 능력"),
    SAL_NETWORKING(JobGroup.SALES, "네트워킹", "고객, 파트너 및 업계 관계자와 지속적인 접점을 형성하고, 신뢰를 바탕으로 협력과 신규 사업 기회를 발굴하는 능력"),
    SAL_EXECUTION(JobGroup.SALES, "실행력", "영업 기회를 발굴한 뒤 고객 접촉, 제안, 후속 대응 등 필요한 활동을 신속하게 수행하고 실제 성과로 연결하는 능력"),
    SAL_INTERPERSONAL(JobGroup.SALES, "대인관계", "고객과 동료의 입장을 이해하고 상황에 맞게 소통하며, 장기적인 신뢰와 협력 관계를 형성하는 능력"),
    SAL_BUSINESS_MIND(JobGroup.SALES, "비즈니스 마인드", "시장 상황, 고객 가치, 매출과 비용 및 수익성을 종합적으로 고려해 사업 기회와 영업 활동의 가치를 판단하는 능력"),
    SAL_PRESENTATION(JobGroup.SALES, "PT", "고객과 의사결정자에게 상품·서비스의 가치와 제안 내용을 논리적으로 구성해 전달하고, 질문과 반론에 근거를 들어 대응하는 능력"),
    SAL_SALES_MANAGEMENT(JobGroup.SALES, "영업 관리", "매출·수주·거래처 등 영업 실적 데이터를 분석하고, 목표와 실적의 차이를 파악해 영업 전략과 자원 배분 및 후속 조치를 결정하는 능력"),

    // ---- 전략기획 ----
    STR_PLANNING(JobGroup.STRATEGY_PLANNING, "계획성", "조직의 목표와 업무 우선순위를 고려해 실행 과제, 일정, 자원 및 단계별 계획을 체계적으로 수립하고 관리하는 능력"),
    STR_ANALYSIS(JobGroup.STRATEGY_PLANNING, "분석력", "시장·산업·경쟁 환경과 내부 사업 데이터를 분석해 핵심 이슈를 도출하고, 전략적 의사결정에 필요한 근거를 마련하는 능력"),
    STR_GOAL_ORIENTATION(JobGroup.STRATEGY_PLANNING, "목표의식", "조직의 목표를 명확하게 이해하고 이를 구체적인 실행 과제와 성과 지표로 전환해 목표 달성까지 업무를 추진하는 능력"),
    STR_CREATIVITY(JobGroup.STRATEGY_PLANNING, "창의성", "기존 사업 방식과 시장의 제약을 새로운 관점에서 바라보고, 사업 경쟁력과 성장 가능성을 높일 대안과 전략을 고안하는 능력"),

    // ---- 재무/회계 ----
    FIN_NUMERACY(JobGroup.FINANCE_ACCOUNTING, "계수 능력", "재무제표, 예산, 매출·비용 등 수치 자료를 정확하게 이해하고 계산하며, 수치 간 관계를 파악해 재무적 판단에 활용하는 능력"),
    FIN_ACCURACY(JobGroup.FINANCE_ACCOUNTING, "정확성", "회계 거래와 재무 자료를 기준과 절차에 따라 꼼꼼하게 검토하고, 계산 오류와 누락을 예방해 신뢰할 수 있는 결과를 산출하는 능력"),
    FIN_HONESTY(JobGroup.FINANCE_ACCOUNTING, "정직", "재무 정보와 거래 내역을 사실에 근거해 기록하고, 오류나 불리한 결과를 임의로 왜곡하지 않으며 회계 원칙과 윤리 기준을 준수하는 태도"),
    FIN_RELIABILITY(JobGroup.FINANCE_ACCOUNTING, "신뢰성", "재무 자료와 보고서를 일관된 기준으로 작성하고 기한과 절차를 준수해, 이해관계자가 의사결정에 활용할 수 있는 정보를 제공하는 능력"),

    // ---- 홍보/광고 ----
    PRAD_INTERPERSONAL(JobGroup.PR_ADVERTISING, "대인관계", "언론, 고객, 협력사 및 내부 구성원과 원활하게 관계를 형성하고, 이해관계자의 입장을 고려해 협력과 업무 조율을 이끌어 내는 능력"),
    PRAD_CREATIVITY(JobGroup.PR_ADVERTISING, "창의성", "브랜드와 캠페인의 목적 및 타깃 특성을 반영해 주목도와 전달력을 높이는 메시지, 콘셉트 및 홍보 아이디어를 기획하는 능력"),
    PRAD_COMMUNICATION(JobGroup.PR_ADVERTISING, "의사소통", "전달하려는 메시지와 대상의 특성을 고려해 핵심 정보를 명확하게 전달하고, 언론·고객·내부 관계자와 효과적으로 소통하는 능력"),
    PRAD_WRITING(JobGroup.PR_ADVERTISING, "문장 구성력", "전달 목적과 클라이언트 특성에 맞춰 핵심 내용을 논리적으로 구성하고, 정확하고 이해하기 쉬운 문장으로 보도자료·광고 문구·홍보 콘텐츠를 작성하는 능력"),

    // ---- 구매/조달 ----
    PUR_NEGOTIATION(JobGroup.PURCHASING, "협상력", "공급업체의 조건과 시장 상황을 비교하고 가격, 품질, 납기 및 계약 조건을 조율해 조직에 유리하면서도 안정적인 거래 조건을 확보하는 능력"),
    PUR_HONESTY(JobGroup.PURCHASING, "정직성", "구매 절차와 계약 기준을 공정하게 준수하고, 이해충돌과 부당한 이익을 피하며 거래 정보와 의사결정 근거를 투명하게 관리하는 태도"),
    PUR_PLANNING(JobGroup.PURCHASING, "계획성", "생산 일정과 수요를 고려해 구매 시점, 수량, 예산 및 납기를 계획하고, 자재 부족이나 과잉 재고를 예방하는 능력"),
    PUR_INFORMATION(JobGroup.PURCHASING, "정보 활용능력", "공급업체, 제품 사양, 가격, 납기 및 계약 조건 등 필요한 정보를 수집·검증하고 구매 의사결정과 공급업체 선정에 활용하는 능력"),
    PUR_MARKET_ANALYSIS(JobGroup.PURCHASING, "시장 분석", "원자재 가격, 공급 동향, 공급업체 경쟁 구도 및 시장 위험을 분석해 구매 비용을 관리하고 안정적인 조달 전략을 수립하는 능력"),

    // ---- 품질관리 ----
    QC_ANALYSIS(JobGroup.QUALITY_CONTROL, "분석력", "검사 결과, 불량률, 고객 불만 및 공정 데이터를 분석해 품질 편차의 패턴과 잠재적인 원인을 파악하는 능력"),
    QC_PROBLEM_SOLVING(JobGroup.QUALITY_CONTROL, "문제해결력", "품질 문제의 근본 원인을 조사하고, 시정·예방 조치를 마련해 재발을 방지하고 제품과 공정의 품질을 개선하는 능력"),
    QC_PLANNING(JobGroup.QUALITY_CONTROL, "계획성", "품질 목표와 기준에 따라 검사 일정, 점검 항목 및 개선 활동을 체계적으로 계획하고 진행 상황을 관리하는 능력"),
    QC_HONESTY(JobGroup.QUALITY_CONTROL, "정직성", "품질 검사와 시험 결과를 사실대로 기록하고 불량이나 기준 미달을 숨기지 않으며, 정해진 품질 기준과 절차를 준수하는 태도"),

    // ---- 생산관리 ----
    PM_PLANNING(JobGroup.PRODUCTION_MGMT, "계획성", "수요, 생산 능력, 자재 수급 및 납기를 고려해 생산 일정과 작업 순서를 계획하고, 계획 대비 실적을 지속적으로 관리하는 능력"),
    PM_GOAL_ORIENTATION(JobGroup.PRODUCTION_MGMT, "목표의식", "생산량, 납기 준수율, 원가 및 생산성 등 목표 지표를 명확하게 이해하고, 목표 달성을 위해 진행 상황과 편차를 관리하는 능력"),
    PM_PROBLEM_SOLVING(JobGroup.PRODUCTION_MGMT, "문제 해결", "생산 지연, 설비 이상, 자재 부족 등 생산 차질의 원인을 파악하고, 생산 일정과 품질에 미치는 영향을 고려해 대응 방안을 마련하는 능력"),
    PM_TEAMWORK(JobGroup.PRODUCTION_MGMT, "협업 능력/ 팀워크", "생산, 품질, 구매, 물류 및 설비 부서와 정보를 공유하고 업무 일정을 조율해 생산 계획을 원활하게 실행하는 능력"),

    // ---- R&D ----
    RND_CREATIVITY(JobGroup.RND, "창의력", "기존 지식과 기술을 새로운 관점에서 결합해 연구 과제의 해결책이나 새로운 제품·기술의 가능성을 고안하는 능력"),
    RND_ANALYSIS(JobGroup.RND, "분석력", "실험 데이터와 관찰 결과를 체계적으로 분석해 가설의 타당성을 검토하고, 결과의 원인과 의미를 도출하는 능력"),
    RND_PERSEVERANCE(JobGroup.RND, "인내력", "연구 과정에서 반복되는 실험과 실패를 감내하고, 결과를 기록·검토하며 가설과 실험 조건을 지속적으로 개선하는 능력"),
    RND_CHALLENGE(JobGroup.RND, "도전", "불확실성이 있는 연구 과제에도 새로운 가설과 방법을 시도하고, 실패와 실험 결과를 바탕으로 새로운 가능성을 탐색하는 능력"),

    // ---- 디자인 ----
    DSN_COMMUNICATION(JobGroup.DESIGN, "의사소통", "사용자와 이해관계자의 요구사항을 정확하게 파악하고, 디자인 의도와 시각적 해결 방안을 명확하게 전달하며 피드백을 조율하는 능력"),
    DSN_DATA_ANALYSIS(JobGroup.DESIGN, "데이터분석", "사용자 조사, 사용성 테스트 및 서비스 이용 데이터를 분석해 사용자의 행동과 불편을 파악하고, 디자인 개선 방향을 도출하는 능력"),
    DSN_TOOL(JobGroup.DESIGN, "디자인 툴 사용 역량", "Figma 등 디자인 도구를 활용해 와이어프레임, 프로토타입 및 UI 시안을 제작하고, 컴포넌트와 디자인 시스템을 활용해 일관성 있는 디자인을 구현하는 능력");

    // D-98 선택 규칙: 공통 역량 최소 개수, 희망 직군 선택 시 그 직군 역량 최소 개수
    // 최대 선택 개수는 [미확정] — 제한 없음 (TODO(NOTICE-043))
    public static final int COMMON_MIN = 1;
    public static final int JOB_MIN = 2;

    private final String label;
    private final CompetencyType type;
    private final JobGroup jobGroup;
    private final String description;

    // 공통 역량
    Competency(String label, String description) {
        this(null, label, description);
    }

    // 직군 역량 (jobGroup이 null이면 공통)
    Competency(JobGroup jobGroup, String label, String description) {
        this.label = label;
        this.type = jobGroup == null ? COMMON : JOB;
        this.jobGroup = jobGroup;
        this.description = description;
    }
}
