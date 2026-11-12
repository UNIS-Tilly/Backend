package com.unis.tilly.user.service;

import com.unis.tilly.global.exception.CustomException;
import com.unis.tilly.global.exception.ErrorCode;
import com.unis.tilly.user.entity.User;
import com.unis.tilly.user.entity.UserCompetency;
import com.unis.tilly.user.enums.Competency;
import com.unis.tilly.user.enums.CompetencyType;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.JobGroup;
import com.unis.tilly.user.enums.SocialProvider;
import com.unis.tilly.user.repository.UserCompetencyRepository;
import com.unis.tilly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalTime;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// 다른 도메인(auth 등)은 이 서비스를 통해서만 접근한다. UserRepository를 직접 쓰지 않는다.
@Service
@RequiredArgsConstructor
public class UserService {

    // 출생연도 하한 [임시] (API_SPEC 4.2). 상한은 올해(Clock 기준)
    // TODO(NOTICE-043): 기획 확정 시 교체
    static final int MIN_BIRTH_YEAR = 1950;

    // 성장 레벨 구간 [임시] (D-101, API_SPEC 4.6): 인덱스+1 = 레벨, 값 = 필요한 누적 COMPLETED 일수(이상)
    // TODO(NOTICE-043): 기획 확정 시 교체
    static final int[] LEVEL_THRESHOLDS = {0, 1, 3, 7, 14, 30, 60, 100, 150};

    private final UserRepository userRepository;
    private final UserCompetencyRepository userCompetencyRepository;
    private final Clock clock;

    public Optional<User> findByProvider(SocialProvider provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    // 3.2 가입: 닉네임 + 선택 gender·birthYear (D-97). 직군·역량은 가입 직후 PUT /users/me/profile로 입력
    public User register(String email, String nickname, SocialProvider provider, String providerUserId,
                         Gender gender, Integer birthYear) {
        validateBirthYear(birthYear);
        User user = new User(email, nickname, provider, providerUserId, gender, birthYear);
        return userRepository.save(user);
    }

    // ST-01
    public UserProfile getProfile(Long userId) {
        User user = findById(userId);
        return new UserProfile(user, findCompetencies(userId));
    }

    // 4.2 프로필 전체 교체 (D-98, D-100)
    @Transactional
    public UserProfile updateProfile(Long userId, ProfileCommand command) {
        validateBirthYear(command.birthYear());
        validateCompetencies(command.targetJobGroup(), command.competencies());

        User user = findById(userId);
        user.changeProfile(command.nickname(), command.gender(), command.birthYear(),
                command.currentJobGroup(), command.targetJobGroup());

        userCompetencyRepository.deleteAllByUserIdInBulk(userId);
        userCompetencyRepository.saveAll(command.competencies().stream()
                .map(competency -> new UserCompetency(userId, competency))
                .toList());

        return new UserProfile(user, sorted(command.competencies()));
    }

    // 4.6 성장 레벨 (D-101): record 도메인이 날짜가 처음 COMPLETED가 될 때 누적 일수로 호출한다.
    @Transactional
    public int raiseLevel(Long userId, long completedDays) {
        User user = findById(userId);
        user.raiseLevel(levelFor(completedDays));
        return user.getLevel();
    }

    public int getLevel(Long userId) {
        return findById(userId).getLevel();
    }

    // 누적 COMPLETED 일수 → 레벨 1~9
    static int levelFor(long completedDays) {
        int level = 1;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (completedDays >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    // 4.4
    @Transactional
    public User updateWorkEndTime(Long userId, LocalTime workEndTime) {
        User user = findById(userId);
        user.changeWorkEndTime(workEndTime);
        return user;
    }

    // ST-03: 구글 쪽 연결 해제는 하지 않는다(D-117) — users 행만 즉시 하드 삭제한다.
    // 역량·할 일 등 사용자 데이터는 DB의 ON DELETE CASCADE로 함께 삭제된다.
    public void withdraw(Long userId, String confirmEmail) {
        User user = findById(userId);
        if (!user.getEmail().equals(confirmEmail)) {
            throw new CustomException(ErrorCode.EMAIL_MISMATCH);
        }
        userRepository.delete(user);
    }

    private void validateBirthYear(Integer birthYear) {
        if (birthYear == null) {
            return;
        }
        int currentYear = Year.now(clock).getValue();
        if (birthYear < MIN_BIRTH_YEAR || birthYear > currentYear) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "출생연도는 " + MIN_BIRTH_YEAR + "~" + currentYear + " 사이여야 합니다.");
        }
    }

    // D-98: 공통 역량 1개 이상, 희망 직군이 있으면 그 직군 역량 2개 이상, 없으면 직군 역량 선택 불가.
    // 최대 선택 개수는 [미확정] — 검사하지 않음 (TODO(NOTICE-043))
    private void validateCompetencies(JobGroup targetJobGroup, List<Competency> competencies) {
        if (competencies.stream().anyMatch(Objects::isNull)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "역량 값이 올바르지 않습니다.");
        }
        if (new HashSet<>(competencies).size() != competencies.size()) {
            throw invalidCompetency("같은 역량을 중복해서 선택할 수 없습니다.");
        }

        long commonCount = competencies.stream().filter(c -> c.getType() == CompetencyType.COMMON).count();
        List<Competency> jobCompetencies = competencies.stream()
                .filter(c -> c.getType() == CompetencyType.JOB)
                .toList();

        if (commonCount < Competency.COMMON_MIN) {
            throw invalidCompetency("공통 역량을 " + Competency.COMMON_MIN + "개 이상 선택해야 합니다.");
        }
        if (targetJobGroup == null) {
            if (!jobCompetencies.isEmpty()) {
                throw invalidCompetency("희망 직군을 선택하지 않으면 직군 역량을 선택할 수 없습니다.");
            }
            return;
        }
        if (jobCompetencies.stream().anyMatch(c -> c.getJobGroup() != targetJobGroup)) {
            throw invalidCompetency("희망 직군에 속한 역량만 선택할 수 있습니다.");
        }
        if (jobCompetencies.size() < Competency.JOB_MIN) {
            throw invalidCompetency("희망 직군 역량을 " + Competency.JOB_MIN + "개 이상 선택해야 합니다.");
        }
    }

    private CustomException invalidCompetency(String message) {
        return new CustomException(ErrorCode.INVALID_COMPETENCY_SELECTION, message);
    }

    private List<Competency> findCompetencies(Long userId) {
        return sorted(userCompetencyRepository.findAllByUserId(userId).stream()
                .map(UserCompetency::getCompetency)
                .toList());
    }

    // 응답 순서를 고정한다 (ENUM 선언 순서: 공통 → 직군별)
    private List<Competency> sorted(List<Competency> competencies) {
        return competencies.stream().sorted().toList();
    }

    private User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    // 4.2 요청 값 (역량 목록은 null이면 빈 목록으로 다룬다)
    public record ProfileCommand(String nickname, Gender gender, Integer birthYear,
                                 JobGroup currentJobGroup, JobGroup targetJobGroup, List<Competency> competencies) {
        public ProfileCommand {
            competencies = competencies == null ? List.of() : competencies;
        }
    }

    public record UserProfile(User user, List<Competency> competencies) {
    }
}
