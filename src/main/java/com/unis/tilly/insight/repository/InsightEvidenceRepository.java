package com.unis.tilly.insight.repository;

import com.unis.tilly.insight.entity.InsightEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsightEvidenceRepository extends JpaRepository<InsightEvidence, Long> {

    List<InsightEvidence> findAllByInsightIdOrderByIdAsc(Long insightId);
}
