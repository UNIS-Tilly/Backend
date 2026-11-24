package com.unis.tilly.user.repository;

import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    // 6.6 자동 질문: 퇴근 시간이 정확히 workEndTime인 사용자 ID (퇴근 시간 미설정 사용자는 null이라 제외)
    @Query("select u.id from User u where u.workEndTime = :workEndTime order by u.id")
    List<Long> findIdsByWorkEndTime(@Param("workEndTime") LocalTime workEndTime);
}
