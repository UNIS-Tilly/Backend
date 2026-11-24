package com.unis.tilly.insight.service;

import com.unis.tilly.ai.dto.WeeklyInsightResult;
import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.insight.entity.InsightAction;
import com.unis.tilly.insight.entity.InsightEvidence;
import com.unis.tilly.insight.entity.InsightImprovement;
import com.unis.tilly.insight.entity.InsightStrength;
import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.insight.enums.EvidenceType;
import com.unis.tilly.insight.enums.InsightStatus;
import com.unis.tilly.insight.enums.StarPart;
import com.unis.tilly.insight.repository.InsightActionRepository;
import com.unis.tilly.insight.repository.InsightEvidenceRepository;
import com.unis.tilly.insight.repository.InsightImprovementRepository;
import com.unis.tilly.insight.repository.InsightStrengthRepository;
import com.unis.tilly.insight.repository.WeeklyInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// 인사이트 저장·상태 전이의 짧은 트랜잭션 모음. AI 호출은 이 클래스 밖(InsightGenerationService)에서 트랜잭션 없이 한다.
@Service
@RequiredArgsConstructor
public class InsightStore {

    private final WeeklyInsightRepository insightRepository;
    private final InsightStrengthRepository strengthRepository;
    private final InsightImprovementRepository improvementRepository;
    private final InsightEvidenceRepository evidenceRepository;
    private final InsightActionRepository actionRepository;
    private final InsightSnapshots snapshots;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<WeeklyInsight> find(Long userId, LocalDate weekStart) {
        return insightRepository.findByUserIdAndWeekStart(userId, weekStart);
    }

    // 조회·요청 시점에 만료된 GENERATING(서버 재시작 등으로 남은 것)을 FAILED로 정리하고 현재 행을 돌려준다 (사용자 결정 2026-11-24)
    @Transactional
    public Optional<WeeklyInsight> findExpiringStale(Long userId, LocalDate weekStart) {
        Optional<WeeklyInsight> found = insightRepository.findByUserIdAndWeekStart(userId, weekStart);
        found.filter(i -> i.isExpiredGenerating(LocalDateTime.now(clock), InsightLockService.GENERATION_TIMEOUT))
                .ifPresent(WeeklyInsight::fail);
        return found;
    }

    // 분석 시작: 행이 없으면 만들고, FAILED(또는 만료)면 같은 행을 GENERATING으로 되돌린다.
    // 이미 PUBLISHED면 409, 아직 유효한 GENERATING이면 새로 시작하지 않고 그대로 돌려준다(started=false, 중복 AI 호출 방지)
    @Transactional
    public Begun begin(Long userId, LocalDate weekStart) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Optional<WeeklyInsight> found = insightRepository.findByUserIdAndWeekStart(userId, weekStart);
        if (found.isEmpty()) {
            return new Begun(insightRepository.save(new WeeklyInsight(userId, weekStart, now)), true);
        }
        WeeklyInsight insight = found.get();
        if (insight.getStatus() == InsightStatus.PUBLISHED) {
            throw new CustomException(ErrorCode.INSIGHT_ALREADY_PUBLISHED);
        }
        if (insight.getStatus() == InsightStatus.GENERATING
                && !insight.isExpiredGenerating(now, InsightLockService.GENERATION_TIMEOUT)) {
            return new Begun(insight, false);
        }
        insight.restart(now);
        return new Begun(insight, true);
    }

    // 발행: 아직 GENERATING일 때만 저장한다(그 사이 만료·실패 처리됐으면 버린다). 발행되면 true
    @Transactional
    public boolean publish(Long insightId, WeeklyInsightResult result, InsightInputAssembler.Assembled assembled) {
        WeeklyInsight insight = insightRepository.findById(insightId).orElse(null);
        if (insight == null || insight.getStatus() != InsightStatus.GENERATING) {
            return false;
        }
        insight.publish(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS),
                assembled.input().insufficientContent(), result.title(), result.subtitle(),
                assembled.summary(), assembled.achievementJson(), assembled.flowJson());

        for (int i = 0; i < result.strengths().size(); i++) {
            WeeklyInsightResult.Strength s = result.strengths().get(i);
            InsightStrength strength = strengthRepository.save(new InsightStrength(insightId, i + 1, s.title(),
                    s.description(), s.competencyCode(), snapshots.write(s.tags())));
            for (WeeklyInsightResult.Evidence e : s.evidences()) {
                evidenceRepository.save(InsightEvidence.ofStrength(insightId, strength.getId(), e.date(),
                        EvidenceType.valueOf(e.type().name())));
            }
        }
        for (int i = 0; i < result.improvements().size(); i++) {
            WeeklyInsightResult.Improvement m = result.improvements().get(i);
            InsightImprovement improvement = improvementRepository.save(new InsightImprovement(insightId, i + 1,
                    m.title(), m.description(), StarPart.valueOf(m.starPart().name())));
            for (WeeklyInsightResult.Evidence e : m.evidences()) {
                evidenceRepository.save(InsightEvidence.ofImprovement(insightId, improvement.getId(), e.date(),
                        EvidenceType.valueOf(e.type().name())));
            }
            actionRepository.save(new InsightAction(insightId, improvement.getId(), m.action()));
        }
        return true;
    }

    @Transactional
    public void markFailed(Long insightId) {
        insightRepository.findById(insightId)
                .filter(i -> i.getStatus() == InsightStatus.GENERATING)
                .ifPresent(WeeklyInsight::fail);
    }

    // 서버 시작 시 남은 GENERATING 정리 (실행 중이던 작업은 서버와 함께 사라졌다)
    @Transactional
    public int failAllGenerating() {
        return insightRepository.failAllGenerating();
    }

    // 발행된 인사이트의 강점·보완·근거·행동 (조회용)
    @Transactional(readOnly = true)
    public Details loadDetails(Long insightId) {
        return new Details(
                strengthRepository.findAllByInsightIdOrderByPositionAsc(insightId),
                improvementRepository.findAllByInsightIdOrderByPositionAsc(insightId),
                evidenceRepository.findAllByInsightIdOrderByIdAsc(insightId),
                actionRepository.findAllByInsightIdOrderByIdAsc(insightId));
    }

    public record Begun(WeeklyInsight insight, boolean started) {
    }

    public record Details(List<InsightStrength> strengths, List<InsightImprovement> improvements,
                          List<InsightEvidence> evidences, List<InsightAction> actions) {
    }
}
