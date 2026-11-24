package com.unis.tilly.record.repository;

import com.unis.tilly.record.entity.DailyReflection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReflectionRepository extends JpaRepository<DailyReflection, Long> {

    Optional<DailyReflection> findByUserIdAndDate(Long userId, LocalDate date);

    // 6.1 reflectionWritten
    boolean existsByUserIdAndDate(Long userId, LocalDate date);
}
