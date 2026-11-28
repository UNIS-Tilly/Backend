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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.unis.tilly.user.enums.Competency.RESILIENCE;
import static com.unis.tilly.user.enums.Competency.PARTNERSHIP;
import static com.unis.tilly.user.enums.Competency.DEV_PROBLEM_SOLVING;
import static com.unis.tilly.user.enums.Competency.DSN_TOOL;
import static com.unis.tilly.user.enums.Competency.DSN_COMMUNICATION;
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

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCompetencyRepository userCompetencyRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userCompetencyRepository);
    }

    // ---- 가입 (3.2) ----

    @Test
    void 가입_시_성별과_나이를_저장하고_레벨은_1이다() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", Gender.FEMALE, 25);

        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getLevel()).isEqualTo(1);
    }

    @Test
    void 가입_시_성별과_나이는_생략할_수_있다() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, null);

        assertThat(user.getGender()).isNull();
        assertThat(user.getAge()).isNull();
    }

    @Test
    void 가입_시_나이가_14_100을_벗어나면_400이고_저장하지_않는다() {
        assertBadRequest(() -> userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, 13));
        assertBadRequest(() -> userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, 101));
        assertBadRequest(() -> userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, -1));
        verify(userRepository, never()).save(any());
    }

    @Test
    void 가입_시_나이_경계값_14와_100은_저장된다() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(userService.register("a@example.com", "틸리", SocialProvider.GOOGLE, "sub", null, 14).getAge())
                .isEqualTo(14);
        assertThat(userService.register("b@example.com", "틸리", SocialProvider.GOOGLE, "sub2", null, 100).getAge())
                .isEqualTo(100);
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
                new UserCompetency(1L, DSN_COMMUNICATION), new UserCompetency(1L, PARTNERSHIP)));

        UserService.UserProfile profile = userService.getProfile(1L);

        assertThat(profile.competencies()).containsExactly(PARTNERSHIP, DSN_COMMUNICATION);
        assertThat(profile.user().getLevel()).isEqualTo(1);
    }

    // ---- 프로필 수정 (4.2) ----

    @Test
    void 프로필을_전체_교체하고_역량을_다시_저장한다() {
        User user = givenUser();

        UserService.UserProfile profile = userService.updateProfile(1L, command(
                "새닉네임", JobGroup.DESIGN, DSN_COMMUNICATION, PARTNERSHIP, DSN_TOOL));

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getCurrentJobGroup()).isEqualTo(JobGroup.IT_DEVELOPMENT);
        assertThat(user.getTargetJobGroup()).isEqualTo(JobGroup.DESIGN);
        assertThat(profile.competencies()).containsExactly(PARTNERSHIP, DSN_COMMUNICATION, DSN_TOOL);
        verify(userCompetencyRepository).deleteAllByUserIdInBulk(1L);
        verify(userCompetencyRepository).saveAll(anyList());
    }

    @Test
    void 희망_직군이_없으면_공통_역량만으로_저장된다() {
        User user = givenUser();

        userService.updateProfile(1L, command("새닉네임", null, PARTNERSHIP));

        assertThat(user.getTargetJobGroup()).isNull();
    }

    @Test
    void 선택_항목을_null로_보내면_해제된다() {
        User user = givenUser();
        user.changeProfile("닉네임", Gender.MALE, 1999, JobGroup.RND, "소재 연구", JobGroup.RND);

        userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, null, null, null, null, List.of(PARTNERSHIP)));

        assertThat(user.getGender()).isNull();
        assertThat(user.getAge()).isNull();
        assertThat(user.getCurrentJobGroup()).isNull();
        assertThat(user.getCurrentJobDetail()).isNull();
        assertThat(user.getTargetJobGroup()).isNull();
    }

    @Test
    void 재직_직군이_있으면_세부_직군을_저장한다() {
        User user = givenUser();

        userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, null, JobGroup.IT_DEVELOPMENT, "백엔드 개발", null, List.of(PARTNERSHIP)));

        assertThat(user.getCurrentJobDetail()).isEqualTo("백엔드 개발");
    }

    @Test
    void 재직_직군_없이_세부_직군만_보내면_BAD_REQUEST() {
        assertBadRequest(() -> userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, null, null, "백엔드 개발", null, List.of(PARTNERSHIP))));
        verify(userCompetencyRepository, never()).saveAll(anyList());
    }

    @Test
    void 공통_역량이_없으면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", JobGroup.DESIGN, DSN_COMMUNICATION, DSN_TOOL));
        assertInvalidCompetency(new UserService.ProfileCommand("닉네임", null, null, null, null, null, null));
    }

    @Test
    void 희망_직군_역량이_2개_미만이면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", JobGroup.DESIGN, PARTNERSHIP, DSN_COMMUNICATION));
    }

    @Test
    void 공통_역량은_최대_3개까지이고_4개면_INVALID_COMPETENCY_SELECTION() {
        // D-144
        assertInvalidCompetency(command("닉네임", null,
                PARTNERSHIP, RESILIENCE, Competency.CUSTOMER_FOCUS, Competency.VISION));
    }

    @Test
    void 공통_역량_3개와_직군_역량_5개는_경계값으로_저장된다() {
        // D-144
        givenUser();

        UserService.UserProfile profile = userService.updateProfile(1L, command("닉네임", JobGroup.IT_DEVELOPMENT,
                PARTNERSHIP, RESILIENCE, Competency.CUSTOMER_FOCUS,
                DEV_PROBLEM_SOLVING, Competency.DEV_PROGRAMMING, Competency.DEV_DATA_ANALYSIS,
                Competency.DEV_TEAMWORK, Competency.DEV_USER_CENTERED));

        assertThat(profile.competencies()).hasSize(8);
    }

    @Test
    void 희망_직군_역량은_최대_5개까지이고_6개면_INVALID_COMPETENCY_SELECTION() {
        // D-144
        assertInvalidCompetency(command("닉네임", JobGroup.IT_DEVELOPMENT, PARTNERSHIP,
                DEV_PROBLEM_SOLVING, Competency.DEV_PROGRAMMING, Competency.DEV_DATA_ANALYSIS,
                Competency.DEV_TEAMWORK, Competency.DEV_USER_CENTERED, Competency.DEV_CREATIVITY));
    }

    @Test
    void 희망_직군이_아닌_직군_역량을_고르면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", JobGroup.DESIGN, PARTNERSHIP, DSN_COMMUNICATION, DSN_TOOL, DEV_PROBLEM_SOLVING));
    }

    @Test
    void 희망_직군_없이_직군_역량을_고르면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", null, PARTNERSHIP, DSN_COMMUNICATION));
    }

    @Test
    void 같은_역량을_중복해서_고르면_INVALID_COMPETENCY_SELECTION() {
        assertInvalidCompetency(command("닉네임", null, PARTNERSHIP, RESILIENCE, PARTNERSHIP));
    }

    @Test
    void 역량_목록에_null이_있으면_400() {
        List<Competency> competencies = new ArrayList<>(Arrays.asList(PARTNERSHIP, null));

        assertBadRequest(() -> userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, null, null, null, null, competencies)));
    }

    @Test
    void 프로필의_나이가_범위를_벗어나면_400() {
        assertBadRequest(() -> userService.updateProfile(1L, new UserService.ProfileCommand(
                "닉네임", null, 101, null, null, null, List.of(PARTNERSHIP))));
    }

    // ---- 성장 레벨 (4.6, D-143) ----

    @Test
    void RC13_퇴근_완료한_날이_하루_늘_때마다_레벨이_1씩_오르고_9_다음은_1이다() {
        assertThat(UserService.levelFor(0)).isEqualTo(1);
        assertThat(UserService.levelFor(1)).isEqualTo(2);
        assertThat(UserService.levelFor(2)).isEqualTo(3);
        assertThat(UserService.levelFor(8)).isEqualTo(9);
        assertThat(UserService.levelFor(9)).isEqualTo(1);   // 순환
        assertThat(UserService.levelFor(10)).isEqualTo(2);
        assertThat(UserService.levelFor(17)).isEqualTo(9);
        assertThat(UserService.levelFor(18)).isEqualTo(1);
    }

    @Test
    void RC13_레벨을_누적_일수로_다시_계산해_저장한다() {
        User user = givenUser();

        assertThat(userService.updateLevel(1L, 7)).isEqualTo(8);
        assertThat(userService.updateLevel(1L, 9)).isEqualTo(1);   // 9 다음은 다시 1
        assertThat(user.getLevel()).isEqualTo(1);
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
        return new UserService.ProfileCommand(nickname, Gender.FEMALE, 25, JobGroup.IT_DEVELOPMENT, null, target,
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
