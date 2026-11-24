package com.unis.tilly.insight.repository;

import com.unis.tilly.insight.entity.InsightImprovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsightImprovementRepository extends JpaRepository<InsightImprovement, Long> {

    List<InsightImprovement> findAllByInsightIdOrderByPositionAsc(Long insightId);
}
