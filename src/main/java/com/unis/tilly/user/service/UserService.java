package com.unis.tilly.user.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
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

    // ST-01
    public User getUser(Long userId) {
        return findById(userId);
    }

    // 4.2
    @Transactional
    public User updateNickname(Long userId, String nickname) {
        User user = findById(userId);
        user.changeNickname(nickname);
        return user;
    }

    // 4.3
    @Transactional
    public User updateWorkEndTime(Long userId, LocalTime workEndTime) {
        User user = findById(userId);
        user.changeWorkEndTime(workEndTime);
        return user;
    }

    // ST-03: 구글 쪽 연결 해제는 하지 않는다(사용자 확정, D-93 이후 결정) — users 행만 즉시 하드 삭제한다.
    public void withdraw(Long userId, String confirmEmail) {
        User user = findById(userId);
        if (!user.getEmail().equals(confirmEmail)) {
            throw new CustomException(ErrorCode.EMAIL_MISMATCH);
        }
        userRepository.delete(user);
    }

    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
