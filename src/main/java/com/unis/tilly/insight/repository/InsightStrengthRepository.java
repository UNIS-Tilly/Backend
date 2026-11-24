package com.unis.tilly.insight.repository;

import com.unis.tilly.insight.entity.InsightStrength;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsightStrengthRepository extends JpaRepository<InsightStrength, Long> {

    List<InsightStrength> findAllByInsightIdOrderByPositionAsc(Long insightId);
}
