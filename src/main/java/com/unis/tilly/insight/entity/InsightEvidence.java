package com.unis.tilly.insight.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import com.unis.tilly.insight.enums.EvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 근거 기록 칩 (날짜 + 종류). 강점·보완 모두 가진다 (D-60). strengthId / improvementId 중 하나만 채워진다.
@Entity
@Table(name = "insight_evidences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsightEvidence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insight_id", nullable = false)
    private Long insightId;

    @Column(name = "strength_id")
    private Long strengthId;

    @Column(name = "improvement_id")
    private Long improvementId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceType type;

    private InsightEvidence(Long insightId, Long strengthId, Long improvementId, LocalDate date, EvidenceType type) {
        this.insightId = insightId;
        this.strengthId = strengthId;
        this.improvementId = improvementId;
        this.date = date;
        this.type = type;
    }

    public static InsightEvidence ofStrength(Long insightId, Long strengthId, LocalDate date, EvidenceType type) {
        return new InsightEvidence(insightId, strengthId, null, date, type);
    }

    public static InsightEvidence ofImprovement(Long insightId, Long improvementId, LocalDate date, EvidenceType type) {
        return new InsightEvidence(insightId, null, improvementId, date, type);
    }
}
