package com.unis.tilly.insight.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import com.unis.tilly.insight.enums.StarPart;
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

// 보완할 부분 (1~2개). starPart: 보완이 필요한 STAR 단계 (D-134)
@Entity
@Table(name = "insight_improvements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsightImprovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "insight_id", nullable = false)
    private Long insightId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "star_part", nullable = false, length = 1)
    private StarPart starPart;

    public InsightImprovement(Long insightId, int position, String title, String description, StarPart starPart) {
        this.insightId = insightId;
        this.position = position;
        this.title = title;
        this.description = description;
        this.starPart = starPart;
    }
}
