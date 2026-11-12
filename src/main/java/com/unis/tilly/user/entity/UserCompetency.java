package com.unis.tilly.user.entity;

import com.unis.tilly.global.common.entity.BaseEntity;
import com.unis.tilly.user.enums.Competency;
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

// 사용자가 얻고 싶은 역량 (user_competencies, D-99). 유니크 (user_id, competency)
@Entity
@Table(name = "user_competencies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCompetency extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Competency competency;

    public UserCompetency(Long userId, Competency competency) {
        this.userId = userId;
        this.competency = competency;
    }
}
