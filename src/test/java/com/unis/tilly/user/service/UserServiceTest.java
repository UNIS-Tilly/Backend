package com.unis.tilly.user.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 4.1~4.3, ST-03
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void 존재하지_않는_사용자를_조회하면_USER_NOT_FOUND() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(1L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 닉네임을_수정한다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        User updated = userService.updateNickname(1L, "바꾼닉네임");

        assertThat(updated.getNickname()).isEqualTo("바꾼닉네임");
    }

    @Test
    void 퇴근_시간을_설정하고_null로_해제한다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.updateWorkEndTime(1L, LocalTime.of(18, 0));
        assertThat(user.getWorkEndTime()).isEqualTo(LocalTime.of(18, 0));

        userService.updateWorkEndTime(1L, null);
        assertThat(user.getWorkEndTime()).isNull();
    }

    @Test
    void 이메일이_일치하면_탈퇴_처리된다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.withdraw(1L, "user@example.com");

        verify(userRepository).delete(user);
    }

    @Test
    void 이메일이_일치하지_않으면_EMAIL_MISMATCH_이고_삭제되지_않는다() {
        User user = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw(1L, "other@example.com"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.EMAIL_MISMATCH));
        verify(userRepository, never()).delete(user);
    }

    private User userWithId(Long id) {
        User user = new User("user@example.com", "닉네임", SocialProvider.GOOGLE, "sub");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
