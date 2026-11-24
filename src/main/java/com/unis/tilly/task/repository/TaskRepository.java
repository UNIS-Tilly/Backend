package com.unis.tilly.task.repository;

import com.unis.tilly.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // 5.1 그날(배정일) 추가한 할 일. 완료/미완료는 서비스에서 나눈다
    List<Task> findAllByUserIdAndDateOrderByIdAsc(Long userId, LocalDate date);

    // 5.3 날짜별 개수 제한 (완료 포함)
    long countByUserIdAndDate(Long userId, LocalDate date);

    Optional<Task> findByIdAndUserId(Long id, Long userId);
}
