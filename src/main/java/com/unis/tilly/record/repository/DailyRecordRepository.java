package com.unis.tilly.record.repository;

import com.unis.tilly.record.entity.DailyRecord;
import com.unis.tilly.record.enums.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, Long> {

    Optional<DailyRecord> findByUserIdAndDate(Long userId, LocalDate date);

    // D-101 성장 레벨: COMPLETED 기록 누적 일수 (유니크 (user_id, date)라 행 수 = 날짜 수)
    long countByUserIdAndStatus(Long userId, RecordStatus status);

    // 7.1 아카이브 달력
    List<DailyRecord> findAllByUserIdAndDateBetweenAndStatusIn(Long userId, LocalDate from, LocalDate to,
                                                               Collection<RecordStatus> statuses);
}
