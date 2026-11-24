package com.unis.tilly.insight.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 추천 행동: 보완 하나당 하나, 20자 이내 (D-57). 홈에 보여주기만 하고 할 일로 반영하지 않는다 (D-147)
@Entity
@Table(name = "insight_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsightAction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insight_id", nullable = false)
    private Long insightId;

    @Column(name = "improvement_id", nullable = false)
    private Long improvementId;

    @Column(nullable = false, length = 20)
    private String text;

    public InsightAction(Long insightId, Long improvementId, String text) {
        this.insightId = insightId;
        this.improvementId = improvementId;
        this.text = text;
    }
}
