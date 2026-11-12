package com.unis.tilly.user.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.JobGroup;
import com.unis.tilly.user.enums.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private static final int INITIAL_LEVEL = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(name = "work_end_time")
    private LocalTime workEndTime;

    // D-96 프로필 (모두 선택 항목)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_job_group", length = 30)
    private JobGroup currentJobGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_job_group", length = 30)
    private JobGroup targetJobGroup;

    // D-101 성장 레벨 1~9. 갱신은 기록 완료 시(record 도메인), API로는 읽기 전용
    @Column(nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    public User(String email, String nickname, SocialProvider provider, String providerUserId) {
        this(email, nickname, provider, providerUserId, null, null);
    }

    public User(String email, String nickname, SocialProvider provider, String providerUserId,
                Gender gender, Integer birthYear) {
        this.email = email;
        this.nickname = nickname;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.gender = gender;
        this.birthYear = birthYear;
        this.level = INITIAL_LEVEL;
    }

    // 4.2 프로필 전체 교체 (역량은 user_competencies에서 따로 교체)
    public void changeProfile(String nickname, Gender gender, Integer birthYear,
                              JobGroup currentJobGroup, JobGroup targetJobGroup) {
        this.nickname = nickname;
        this.gender = gender;
        this.birthYear = birthYear;
        this.currentJobGroup = currentJobGroup;
        this.targetJobGroup = targetJobGroup;
    }

    public void changeWorkEndTime(LocalTime workEndTime) {
        this.workEndTime = workEndTime;
    }
}
