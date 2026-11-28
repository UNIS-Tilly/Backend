package com.unis.tilly.task.repository;

import com.unis.tilly.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // 5.1 그날(배정일) 추가한 할 일. 완료/미완료는 서비스에서 나눈다
    List<Task> findAllByUserIdAndDateOrderByIdAsc(Long userId, LocalDate date);

    // 9.1 인사이트 주간 집계: 기간(배정일) 안의 할 일
    List<Task> findAllByUserIdAndDateBetweenOrderByDateAscIdAsc(Long userId, LocalDate from, LocalDate to);

    // 5.3 날짜별 개수 제한 (완료 포함)
    long countByUserIdAndDate(Long userId, LocalDate date);

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    // 알림(D-152): 마감이 (from, until] 안이고 아직 완료하지 않은 할 일
    @Query("select t from Task t where t.completed = false and t.dueDate > :from and t.dueDate <= :until order by t.id")
    List<Task> findDueBetweenNotCompleted(@Param("from") LocalDateTime from, @Param("until") LocalDateTime until);
}
