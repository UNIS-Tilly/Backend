-- 주간 인사이트 (API_SPEC 9장, 10장). 행이 없는 주는 응답에서 NOT_PUBLISHED로 표현한다.
-- status: GENERATING(생성 중, 그 주 잠금) / PUBLISHED(발행, 그 주 잠금) / FAILED(실패, 재요청 허용)
-- 제목·소제목·주간 달성·업무 흐름은 발행 시점 값을 그대로 저장한다(스냅샷, 이전 주 할 일이 나중에 바뀌어도 숫자가 어긋나지 않도록).
CREATE TABLE weekly_insights (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    generated_at DATETIME(6) NULL,
    insufficient_content BOOLEAN NOT NULL DEFAULT FALSE,
    title VARCHAR(255) NULL,
    subtitle VARCHAR(255) NULL,
    recorded_days INT NULL,
    memo_count INT NULL,
    reflection_count INT NULL,
    answer_count INT NULL,
    total_count INT NULL,
    completed_count INT NULL,
    weekly_achievement JSON NULL,
    weekly_flow JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_weekly_insights_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_weekly_insights_user_id_week_start UNIQUE (user_id, week_start),
    INDEX idx_weekly_insights_user_status_week (user_id, status, week_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 강점: 역량 코드(D-134, 선택 역량 중 근거가 있을 때만), 태그는 JSON 배열
CREATE TABLE insight_strengths (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    insight_id BIGINT NOT NULL,
    position INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    competency_code VARCHAR(40) NULL,
    tags JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_insight_strengths_insight FOREIGN KEY (insight_id) REFERENCES weekly_insights (id) ON DELETE CASCADE,
    INDEX idx_insight_strengths_insight_id (insight_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 보완: STAR 구분(D-134) S/T/A/R
CREATE TABLE insight_improvements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    insight_id BIGINT NOT NULL,
    position INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    star_part VARCHAR(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_insight_improvements_insight FOREIGN KEY (insight_id) REFERENCES weekly_insights (id) ON DELETE CASCADE,
    INDEX idx_insight_improvements_insight_id (insight_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 근거 기록: 강점·보완 모두 가진다(D-60). strength_id / improvement_id 중 하나만 채워진다.
CREATE TABLE insight_evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    insight_id BIGINT NOT NULL,
    strength_id BIGINT NULL,
    improvement_id BIGINT NULL,
    date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_insight_evidences_insight FOREIGN KEY (insight_id) REFERENCES weekly_insights (id) ON DELETE CASCADE,
    CONSTRAINT fk_insight_evidences_strength FOREIGN KEY (strength_id) REFERENCES insight_strengths (id) ON DELETE CASCADE,
    CONSTRAINT fk_insight_evidences_improvement FOREIGN KEY (improvement_id) REFERENCES insight_improvements (id) ON DELETE CASCADE,
    CONSTRAINT ck_insight_evidences_owner CHECK ((strength_id IS NULL) <> (improvement_id IS NULL)),
    INDEX idx_insight_evidences_insight_id (insight_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 추천 행동: 보완 하나당 하나, 20자 이내(D-57). 홈에 보여주기만 하고 할 일로 반영하지 않는다(D-147)
CREATE TABLE insight_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    insight_id BIGINT NOT NULL,
    improvement_id BIGINT NOT NULL,
    text VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_insight_actions_insight FOREIGN KEY (insight_id) REFERENCES weekly_insights (id) ON DELETE CASCADE,
    CONSTRAINT fk_insight_actions_improvement FOREIGN KEY (improvement_id) REFERENCES insight_improvements (id) ON DELETE CASCADE,
    INDEX idx_insight_actions_insight_id (insight_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
