-- 하루 회고: 사용자·날짜당 1건, 2,000자 이하. 기록 행(daily_records)이 없는 날에도 자동저장될 수 있어 (user_id, date)로 식별한다.
-- 내용이 비면 행을 두지 않는다.
CREATE TABLE daily_reflections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    date DATE NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_daily_reflections_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_daily_reflections_user_id_date UNIQUE (user_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
