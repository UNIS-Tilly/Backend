package com.unis.tilly.user.service;

import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

// 다른 도메인(auth 등)은 이 서비스를 통해서만 접근한다. UserRepository를 직접 쓰지 않는다.
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Optional<User> findByProvider(SocialProvider provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    public User register(String email, String nickname, SocialProvider provider, String providerUserId) {
        User user = new User(email, nickname, provider, providerUserId);
        return userRepository.save(user);
    }
}
