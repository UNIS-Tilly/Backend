package com.unis.tilly.insight.repository;

import com.unis.tilly.insight.entity.InsightAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsightActionRepository extends JpaRepository<InsightAction, Long> {

    List<InsightAction> findAllByInsightIdOrderByIdAsc(Long insightId);
}
