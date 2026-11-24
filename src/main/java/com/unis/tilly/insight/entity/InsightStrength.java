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

// 발견한 강점 (1~2개). competencyCode는 사용자가 선택한 역량 중 근거가 있을 때만(없으면 null, D-134)
@Entity
@Table(name = "insight_strengths")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsightStrength extends BaseEntity {

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

    @Column(name = "competency_code", length = 40)
    private String competencyCode;

    // JSON 배열 문자열
    @Column(nullable = false, columnDefinition = "json")
    private String tags;

    public InsightStrength(Long insightId, int position, String title, String description,
                           String competencyCode, String tags) {
        this.insightId = insightId;
        this.position = position;
        this.title = title;
        this.description = description;
        this.competencyCode = competencyCode;
        this.tags = tags;
    }
}
