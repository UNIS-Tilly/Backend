package com.unis.tilly.record.repository;

import com.unis.tilly.record.entity.RecordQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordQuestionRepository extends JpaRepository<RecordQuestion, Long> {

    List<RecordQuestion> findAllByRecordIdOrderByOrderAsc(Long recordId);
}
