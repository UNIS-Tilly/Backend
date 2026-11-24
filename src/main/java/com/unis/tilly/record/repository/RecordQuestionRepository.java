package com.unis.tilly.record.repository;

import com.unis.tilly.record.entity.RecordQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecordQuestionRepository extends JpaRepository<RecordQuestion, Long> {

    List<RecordQuestion> findAllByRecordIdOrderByOrderAsc(Long recordId);

    // 9.2 인사이트 입력: 여러 기록의 질문
    List<RecordQuestion> findAllByRecordIdInOrderByRecordIdAscOrderAsc(Collection<Long> recordIds);
}
