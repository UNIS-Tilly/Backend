package com.unis.tilly.user.repository;

import com.unis.tilly.user.entity.UserCompetency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCompetencyRepository extends JpaRepository<UserCompetency, Long> {

    List<UserCompetency> findAllByUserId(Long userId);

    // 전체 교체 시 기존 행을 먼저 지운다. 즉시 실행되는 벌크 삭제라서
    // 같은 역량을 다시 넣어도 유니크 제약 (user_id, competency)과 충돌하지 않는다.
    @Modifying(flushAutomatically = true)
    @Query("delete from UserCompetency uc where uc.userId = :userId")
    void deleteAllByUserIdInBulk(@Param("userId") Long userId);
}
