package com.unis.tilly.user.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.entity.UserCompetency;
import com.unis.tilly.user.enums.Competency;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.JobGroup;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.repository.UserCompetencyRepository;
import com.unis.tilly.user.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.unis.tilly.user.enums.Competency.COLLABORATION;
import static com.unis.tilly.user.enums.Competency.COMMUNICATION;
import static com.unis.tilly.user.enums.Competency.DEV_DEBUGGING;
import static com.unis.tilly.user.enums.Competency.DSN_PROTOTYPING;
import static com.unis.tilly.user.enums.Competency.DSN_UX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 4.1, 4.2, 4.4, 4.7(ST-03), 3.2 가입 (D-96~D-101, D-117)
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCompetencyRepository userCompetencyRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 12).atStartOfDay(SEOUL).toInstant(), SEOUL);
        userService = new UserService(userRepository, userCompetencyRepository, clock);
    }

    // ---- 가입 (3.2) ----

    @Test
    void 가입_시_성별과_출생연도를_저장하고_레벨은_1이다() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", Gender.FEMALE, 2001);

        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getBirthYear()).isEqualTo(2001);
        assertThat(user.getLevel()).isEqualTo(1);
    }

    @Test
    void 가입_시_성별과_출생연도는_생략할_수_있다() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, null);

        assertThat(user.getGender()).isNull();
        assertThat(user.getBirthYear()).isNull();
    }

    @Test
    void 가입_시_출생연도가_범위를_벗어나면_400이고_저장하지_않는다() {
        assertBadRequest(() -> userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, 1949));
        assertBadRequest(() -> userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, 2027));
        verify(userRepository, never()).save(any());
    }

    // ---- 내 정보 (ST-01) ----

    @Test
    void ST01_존재하지_않는_사용자를_조회하면_USER_NOT_FOUND() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void ST01_역량을_선언_순서로_정렬해_돌려준다() {
        givenUser();
        given(userCompetencyRepository.findAllByUserId(1L)).willReturn(List.of(
                new UserCompetency(1L, DSN_UX), new UserCompetency(1L, COMMUNICATION)));

        UserService.UserProfile profile = userService.getProfile(1L);

        assertThat(profile.competencies()).containsExactly(COMMUNICATION, DSN_UX);
        assertThat(profile.user().getLevel()).isEqualTo(1);
    }

    // ---- 프로필 수정 (4.2) ----

    @Test
    void 프로필을_전체_교체하고_역량을_다시_저장한다() {
        User user = givenUser();

        UserService.UserProfile profile = userService.updateProfile(1L, command(
                "새닉네임", JobGroup.DESIGN, DSN_UX, COMMUNICATION, DSN_PROTOTYPING));

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getBirthYear()).isEqualTo(2001);
        assertThat(user.getCurrentJobGroup()).isEqualTo(JobGroup.DEVELOPMENT);
        assertThat(user.getTargetJobGroup()).isEqualTo(JobGroup.DESIGN);
        assertThat(profile.competencies()).containsExactly(COMMUNICATION, DSN_UX, DSN_PROTOTYPING);
        verify(userCompetencyRepository).deleteAllByUserIdInBulk(1L);
        verify(userCompetencyRepository).saveAll(anyList());
    }

    @Test
    void 희망_직군이_없으면_공통_역량만으로_저장된다() {
        User user = givenUser();

        userService.updateProfile(1L, command("새닉네임", null, COMMUNICATION));

        assertThat(user.getTargetJobGroup()).isNull();
    }

    @Test
    void 선택_항목을_null로_보내면_해제된다() {
        User user = givenUser();
        user.changeProfile("닉네임", Gender.MALE, 1999, JobGroup.DATA, JobGroup.DATA);

        userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, null, null, null, List.of(COMMUNICATION)));

        assertThat(user.getGender()).isNull();
        assertThat(user.getBirthYear()).isNull();
        assertThat(user.getCurrentJobGroup()).isNull();
        assertThat(user.getTargetJobGroup()).isNull();
    }

    @Test
    void 공통_역량이_없으면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", JobGroup.DESIGN, DSN_UX, DSN_PROTOTYPING));
        assertInvalidCompetency(new UserService.ProfileCommand("닉네임", null, null, null, null, null));
    }

    @Test
    void 희망_직군_역량이_2개_미만이면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", JobGroup.DESIGN, COMMUNICATION, DSN_UX));
    }

    @Test
    void 희망_직군이_아닌_직군_역량을_고르면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", JobGroup.DESIGN, COMMUNICATION, DSN_UX, DSN_PROTOTYPING, DEV_DEBUGGING));
    }

    @Test
    void 희망_직군_없이_직군_역량을_고르면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", null, COMMUNICATION, DSN_UX));
    }

    @Test
    void 같은_역량을_중복해서_고르면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", null, COMMUNICATION, COLLABORATION, COMMUNICATION));
    }

    @Test
    void 역량_목록에_null이_있으면_400() {
        List<Competency> competencies = new ArrayList<>(Arrays.asList(COMMUNICATION, null));

        assertBadRequest(() -> userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, null, null, null, competencies)));
    }

    @Test
    void 프로필의_출생연도가_범위를_벗어나면_400() {
        assertBadRequest(() -> userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, 1900, null, null, List.of(COMMUNICATION))));
    }

    // ---- 성장 레벨 (4.6, D-101) ----

    @Test
    void RC13_누적_완료_일수로_레벨_구간을_계산한다() {
        assertThat(UserService.levelFor(0)).isEqualTo(1);
        assertThat(UserService.levelFor(1)).isEqualTo(2);
        assertThat(UserService.levelFor(2)).isEqualTo(2);
        assertThat(UserService.levelFor(3)).isEqualTo(3);
        assertThat(UserService.levelFor(149)).isEqualTo(8);
        assertThat(UserService.levelFor(150)).isEqualTo(9);
        assertThat(UserService.levelFor(1000)).isEqualTo(9);
    }

    @Test
    void RC13_레벨은_올라가기만_하고_내려가지_않는다() {
        User user = givenUser();

        assertThat(userService.raiseLevel(1L, 7)).isEqualTo(4);
        assertThat(userService.raiseLevel(1L, 1)).isEqualTo(4);
        assertThat(user.getLevel()).isEqualTo(4);
    }

    // ---- 퇴근 시간 (4.4) ----

    @Test
    void 퇴근_시간을_설정하고_null로_해제한다() {
        User user = givenUser();

        userService.updateWorkEndTime(1L, LocalTime.of(18, 0));
        assertThat(user.getWorkEndTime()).isEqualTo(LocalTime.of(18, 0));

        userService.updateWorkEndTime(1L, null);
        assertThat(user.getWorkEndTime()).isNull();
    }

    // ---- 탈퇴 (ST-03) ----

    @Test
    void ST03_이메일이_일치하면_탈퇴_처리된다() {
        User user = givenUser();

        userService.withdraw(1L, "user@example.com");

        verify(userRepository).delete(user);
    }

    @Test
    void ST03_이메일이_일치하지_않으면_EMAIL_MISMATCH_이고_삭제되지_않는다() {
        User user = givenUser();

        assertThatThrownBy(() -> userService.withdraw(1L, "other@example.com"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.EMAIL_MISMATCH));
        verify(userRepository, never()).delete(user);
    }

    private UserService.ProfileCommand command(String nickname, JobGroup target, Competency... competencies) {
        return new UserService.ProfileCommand(nickname, Gender.FEMALE, 2001, JobGroup.DEVELOPMENT, target,
                List.of(competencies));
    }

    private void assertInvalidCompetency(UserService.ProfileCommand command) {
        assertThatThrownBy(() -> userService.updateProfile(1L, command))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_COMPETENCY_SELECTION));
        verify(userCompetencyRepository, never()).saveAll(anyList());
    }

    private void assertBadRequest(ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    private User givenUser() {
        User user = new User("user@example.com", "닉네임", SocialProvider.GOOGLE, "sub");
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        return user;
    }
}
