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

    // 5.1 완료 목록: 완료일 = day
    List<Task> findAllByUserIdAndCompletedDateOrderByIdAsc(Long userId, LocalDate completedDate);

    // 5.1 미완료 목록: day 시점에 끝나지 않았고, 배정일 ≤ day 이거나 배정일이 없고 생성일 ≤ day
    // nextDayStart = (day + 1일) 00:00 — 생성 시각이 그 전이면 생성일 ≤ day
    @Query("""
            select t from Task t
            where t.userId = :userId
              and (t.completedDate is null or t.completedDate > :day)
              and ((t.date is not null and t.date <= :day)
                   or (t.date is null and t.createdAt < :nextDayStart))
            order by t.id asc
            """)
    List<Task> findAllIncompleteVisibleOn(@Param("userId") Long userId,
                                          @Param("day") LocalDate day,
                                          @Param("nextDayStart") LocalDateTime nextDayStart);

    long countByUserIdAndCompletedDate(Long userId, LocalDate completedDate);

    @Query("""
            select count(t) from Task t
            where t.userId = :userId
              and (t.completedDate is null or t.completedDate > :day)
              and ((t.date is not null and t.date <= :day)
                   or (t.date is null and t.createdAt < :nextDayStart))
            """)
    long countIncompleteVisibleOn(@Param("userId") Long userId,
                                  @Param("day") LocalDate day,
                                  @Param("nextDayStart") LocalDateTime nextDayStart);

    Optional<Task> findByIdAndUserId(Long id, Long userId);
}
