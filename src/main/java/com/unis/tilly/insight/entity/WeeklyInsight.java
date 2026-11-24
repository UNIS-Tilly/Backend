package com.unis.tilly.insight.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import com.unis.tilly.insight.enums.InsightStatus;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 주간 인사이트 (사용자·주당 1건). 행이 없으면 NOT_PUBLISHED. GENERATING·PUBLISHED이면 그 주 기록이 잠긴다 (D-137)
@Entity
@Table(name = "weekly_insights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyInsight extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 그 주의 월요일
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InsightStatus status;

    // 분석을 요청한 시각. GENERATING이 오래 남았는지(만료) 판단에 쓴다
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    // 메모·회고·답변이 하나도 없어 할 일 제목만으로 분석함 (warnings: INSUFFICIENT_CONTENT, D-54)
    @Column(name = "insufficient_content", nullable = false)
    private boolean insufficientContent;

    private String title;
    private String subtitle;

    @Column(name = "recorded_days")
    private Integer recordedDays;
    @Column(name = "memo_count")
    private Integer memoCount;
    @Column(name = "reflection_count")
    private Integer reflectionCount;
    @Column(name = "answer_count")
    private Integer answerCount;
    @Column(name = "total_count")
    private Integer totalCount;
    @Column(name = "completed_count")
    private Integer completedCount;

    // 발행 시점 스냅샷(JSON 문자열). 직렬화·역직렬화는 InsightSnapshots가 한다
    @Column(name = "weekly_achievement", columnDefinition = "json")
    private String weeklyAchievement;
    @Column(name = "weekly_flow", columnDefinition = "json")
    private String weeklyFlow;

    public WeeklyInsight(Long userId, LocalDate weekStart, LocalDateTime requestedAt) {
        this.userId = userId;
        this.weekStart = weekStart;
        this.status = InsightStatus.GENERATING;
        this.requestedAt = requestedAt;
    }

    // FAILED(또는 만료된 GENERATING) 뒤 재요청: 같은 행을 다시 GENERATING으로 (D-139)
    public void restart(LocalDateTime requestedAt) {
        this.status = InsightStatus.GENERATING;
        this.requestedAt = requestedAt;
    }

    public void fail() {
        this.status = InsightStatus.FAILED;
    }

    public void publish(LocalDateTime generatedAt, boolean insufficientContent, String title, String subtitle,
                        Summary summary, String weeklyAchievement, String weeklyFlow) {
        this.status = InsightStatus.PUBLISHED;
        this.generatedAt = generatedAt;
        this.insufficientContent = insufficientContent;
        this.title = title;
        this.subtitle = subtitle;
        this.recordedDays = summary.recordedDays();
        this.memoCount = summary.memoCount();
        this.reflectionCount = summary.reflectionCount();
        this.answerCount = summary.answerCount();
        this.totalCount = summary.totalCount();
        this.completedCount = summary.completedCount();
        this.weeklyAchievement = weeklyAchievement;
        this.weeklyFlow = weeklyFlow;
    }

    // 요청 뒤 timeout을 넘겨 GENERATING이 남아 있으면 만료로 본다 (서버 재시작·비정상 종료 대비)
    public boolean isExpiredGenerating(LocalDateTime now, Duration timeout) {
        return status == InsightStatus.GENERATING && requestedAt.plus(timeout).isBefore(now);
    }

    // D-137: GENERATING(만료 전)이거나 PUBLISHED이면 그 주 기록·할 일·회고를 잠근다. FAILED는 풀린다
    public boolean locksWeek(LocalDateTime now, Duration timeout) {
        return status == InsightStatus.PUBLISHED
                || (status == InsightStatus.GENERATING && !isExpiredGenerating(now, timeout));
    }

    public record Summary(int recordedDays, int memoCount, int reflectionCount, int answerCount,
                          int totalCount, int completedCount) {
    }
}
