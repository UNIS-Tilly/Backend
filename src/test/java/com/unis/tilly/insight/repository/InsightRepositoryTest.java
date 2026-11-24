package com.unis.tilly.insight.repository;

import com.unis.tilly.insight.entity.InsightAction;
import com.unis.tilly.insight.entity.InsightEvidence;
import com.unis.tilly.insight.entity.InsightImprovement;
import com.unis.tilly.insight.entity.InsightStrength;
import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.enums.EvidenceType;
import com.unis.tilly.insight.enums.InsightStatus;
import com.unis.tilly.insight.enums.StarPart;
import com.unis.tilly.global.config.ClockConfig;
import com.unis.tilly.global.config.JpaAuditingConfig;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V11 마이그레이션의 제약·조회를 실제 MySQL로 확인한다 (유니크, 근거 소유 CHECK, CASCADE, 정렬·페이지)
// 슬라이스 테스트는 @Configuration을 읽지 않으므로 생성·수정 시각(Auditing)에 필요한 설정을 직접 불러온다.
// (없으면 단독 실행 시 created_at이 null로 들어가 실패한다. 전체 실행에서는 다른 테스트가 남긴 상태에 가려져 있었다.)
@DataJpaTest
@Import({ClockConfig.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class InsightRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 10, 0, 0);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private WeeklyInsightRepository insightRepository;
    @Autowired
    private InsightStrengthRepository strengthRepository;
    @Autowired
    private InsightImprovementRepository improvementRepository;
    @Autowired
    private InsightEvidenceRepository evidenceRepository;
    @Autowired
    private InsightActionRepository actionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager em;

    private Long userId;
    private int seq;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    private Long newUser() {
        return userRepository.saveAndFlush(new User("u" + (seq++) + "@example.com", "틸리", SocialProvider.GOOGLE,
                "sub-" + System.nanoTime() + "-" + seq)).getId();
    }

    private WeeklyInsight published(Long user, LocalDate weekStart) {
        WeeklyInsight insight = new WeeklyInsight(user, weekStart, NOW);
        insight.publish(NOW.plusMinutes(1), false, "제목 " + weekStart, "소제목", new WeeklyInsight.Summary(3, 1, 1, 1, 4, 2),
                "{\"totalCount\":4,\"completedCount\":2,\"days\":[]}", "[]");
        return insightRepository.saveAndFlush(insight);
    }

    @Test
    void IN03_사용자와_주당_인사이트는_하나만_둘_수_있다() {
        insightRepository.saveAndFlush(new WeeklyInsight(userId, LocalDate.of(2026, 10, 19), NOW));

        assertThatThrownBy(() -> insightRepository.saveAndFlush(new WeeklyInsight(userId, LocalDate.of(2026, 10, 19), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void IN03_다른_주나_다른_사용자의_인사이트는_따로_둘_수_있다() {
        insightRepository.saveAndFlush(new WeeklyInsight(userId, LocalDate.of(2026, 10, 19), NOW));

        insightRepository.saveAndFlush(new WeeklyInsight(userId, LocalDate.of(2026, 10, 26), NOW));
        insightRepository.saveAndFlush(new WeeklyInsight(newUser(), LocalDate.of(2026, 10, 19), NOW));

        assertThat(insightRepository.count()).isEqualTo(3);
    }

    @Test
    void IN03_발행하면_스냅샷_JSON과_수치가_그대로_저장된다() {
        WeeklyInsight saved = published(userId, LocalDate.of(2026, 10, 19));
        em.clear();

        WeeklyInsight loaded = insightRepository.findByUserIdAndWeekStart(userId, LocalDate.of(2026, 10, 19)).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(InsightStatus.PUBLISHED);
        assertThat(loaded.getTitle()).isEqualTo("제목 2026-10-19");
        assertThat(loaded.getTotalCount()).isEqualTo(4);
        assertThat(loaded.getWeeklyAchievement()).contains("\"totalCount\"").contains("4");
        assertThat(loaded.getWeeklyFlow()).isEqualTo("[]");
        assertThat(loaded.getId()).isEqualTo(saved.getId());
    }

    @Test
    void IN06_강점과_보완의_근거는_둘_중_하나에만_붙고_둘_다_또는_둘_다_없으면_거절된다() {
        WeeklyInsight insight = published(userId, LocalDate.of(2026, 10, 19));
        InsightStrength strength = strengthRepository.saveAndFlush(new InsightStrength(insight.getId(), 1, "강점", "설명", null, "[]"));
        InsightImprovement improvement = improvementRepository.saveAndFlush(
                new InsightImprovement(insight.getId(), 1, "보완", "설명", StarPart.T));

        evidenceRepository.saveAndFlush(InsightEvidence.ofStrength(insight.getId(), strength.getId(), LocalDate.of(2026, 10, 21), EvidenceType.MEMO));
        evidenceRepository.saveAndFlush(InsightEvidence.ofImprovement(insight.getId(), improvement.getId(), LocalDate.of(2026, 10, 22), EvidenceType.TASK));
        assertThat(evidenceRepository.findAllByInsightIdOrderByIdAsc(insight.getId())).hasSize(2);

        // 둘 다 채우거나 둘 다 비우는 근거는 CHECK 제약으로 거절
        InsightEvidence both = InsightEvidence.ofStrength(insight.getId(), strength.getId(), LocalDate.of(2026, 10, 21), EvidenceType.MEMO);
        ReflectionUtilsHelper.set(both, "improvementId", improvement.getId());
        assertThatThrownBy(() -> evidenceRepository.saveAndFlush(both)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void IN06_인사이트를_지우면_강점_보완_근거_행동이_함께_삭제된다() {
        WeeklyInsight insight = published(userId, LocalDate.of(2026, 10, 19));
        InsightStrength strength = strengthRepository.saveAndFlush(new InsightStrength(insight.getId(), 1, "강점", "설명", "PARTNERSHIP", "[\"태그\"]"));
        InsightImprovement improvement = improvementRepository.saveAndFlush(new InsightImprovement(insight.getId(), 1, "보완", "설명", StarPart.R));
        evidenceRepository.saveAndFlush(InsightEvidence.ofStrength(insight.getId(), strength.getId(), LocalDate.of(2026, 10, 21), EvidenceType.REFLECTION));
        actionRepository.saveAndFlush(new InsightAction(insight.getId(), improvement.getId(), "성과 수치 기록하기"));

        insightRepository.deleteById(insight.getId());
        insightRepository.flush();
        em.clear();

        assertThat(strengthRepository.findAllByInsightIdOrderByPositionAsc(insight.getId())).isEmpty();
        assertThat(improvementRepository.findAllByInsightIdOrderByPositionAsc(insight.getId())).isEmpty();
        assertThat(evidenceRepository.findAllByInsightIdOrderByIdAsc(insight.getId())).isEmpty();
        assertThat(actionRepository.findAllByInsightIdOrderByIdAsc(insight.getId())).isEmpty();
    }

    @Test
    void IN06_강점과_보완은_순서대로_돌려준다() {
        WeeklyInsight insight = published(userId, LocalDate.of(2026, 10, 19));
        strengthRepository.saveAndFlush(new InsightStrength(insight.getId(), 2, "둘째", "설명", null, "[]"));
        strengthRepository.saveAndFlush(new InsightStrength(insight.getId(), 1, "첫째", "설명", null, "[]"));

        assertThat(strengthRepository.findAllByInsightIdOrderByPositionAsc(insight.getId()))
                .extracting(InsightStrength::getTitle).containsExactly("첫째", "둘째");
    }

    @Test
    void HM12_가장_최근에_발행된_인사이트만_찾고_생성_중_실패는_제외한다() {
        published(userId, LocalDate.of(2026, 10, 12));
        published(userId, LocalDate.of(2026, 10, 19));
        insightRepository.saveAndFlush(new WeeklyInsight(userId, LocalDate.of(2026, 10, 26), NOW));   // GENERATING

        WeeklyInsight latest = insightRepository
                .findFirstByUserIdAndStatusOrderByWeekStartDesc(userId, InsightStatus.PUBLISHED).orElseThrow();

        assertThat(latest.getWeekStart()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(insightRepository.findFirstByUserIdAndStatusOrderByWeekStartDesc(newUser(), InsightStatus.PUBLISHED)).isEmpty();
    }

    @Test
    void IN01_목록은_발행된_주만_정렬과_페이지로_돌려주고_다른_사용자는_섞이지_않는다() {
        published(userId, LocalDate.of(2026, 10, 5));
        published(userId, LocalDate.of(2026, 10, 12));
        published(userId, LocalDate.of(2026, 10, 19));
        WeeklyInsight failed = new WeeklyInsight(userId, LocalDate.of(2026, 10, 26), NOW);
        failed.fail();
        insightRepository.saveAndFlush(failed);
        published(newUser(), LocalDate.of(2026, 10, 19));

        var latestFirst = insightRepository.findAllByUserIdAndStatus(userId, InsightStatus.PUBLISHED,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "weekStart")));
        var oldestFirst = insightRepository.findAllByUserIdAndStatus(userId, InsightStatus.PUBLISHED,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "weekStart")));

        assertThat(latestFirst.getTotalElements()).isEqualTo(3);   // 실패·다른 사용자 제외
        assertThat(latestFirst.hasNext()).isTrue();
        assertThat(latestFirst.getContent()).extracting(WeeklyInsight::getWeekStart)
                .containsExactly(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 12));
        assertThat(oldestFirst.getContent()).extracting(WeeklyInsight::getWeekStart)
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19));
    }

    @Test
    void IN03_서버_시작_정리는_GENERATING만_FAILED로_바꾼다() {
        WeeklyInsight generating = insightRepository.saveAndFlush(new WeeklyInsight(userId, LocalDate.of(2026, 10, 19), NOW));
        WeeklyInsight published = published(userId, LocalDate.of(2026, 10, 12));

        int changed = insightRepository.failAllGenerating();
        em.clear();

        assertThat(changed).isEqualTo(1);
        assertThat(insightRepository.findById(generating.getId()).orElseThrow().getStatus()).isEqualTo(InsightStatus.FAILED);
        assertThat(insightRepository.findById(published.getId()).orElseThrow().getStatus()).isEqualTo(InsightStatus.PUBLISHED);
    }

    @Test
    void IN03_사용자를_지우면_인사이트도_함께_삭제된다() {
        Long other = newUser();
        published(other, LocalDate.of(2026, 10, 19));

        userRepository.deleteById(other);
        userRepository.flush();
        em.clear();

        assertThat(insightRepository.findByUserIdAndWeekStart(other, LocalDate.of(2026, 10, 19))).isEmpty();
    }

    // 엔티티 필드를 직접 세팅하는 테스트 보조 (CHECK 제약 위반 케이스 생성용)
    private static final class ReflectionUtilsHelper {
        static void set(Object target, String field, Object value) {
            org.springframework.test.util.ReflectionTestUtils.setField(target, field, value);
        }
    }
}
