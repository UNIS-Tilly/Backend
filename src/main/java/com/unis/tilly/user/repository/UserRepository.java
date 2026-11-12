package com.unis.tilly.user.repository;

import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
