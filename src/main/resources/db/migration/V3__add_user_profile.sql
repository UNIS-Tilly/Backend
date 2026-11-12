-- 회원 프로필 확장 (API_SPEC 4장, D-96~D-101)
-- 성별·직군은 ENUM 이름을 문자열로 저장한다. 키워드 목록은 코드의 ENUM으로 관리 (D-99)
ALTER TABLE users
    ADD COLUMN gender VARCHAR(20) NULL AFTER work_end_time,
    ADD COLUMN birth_year INT NULL AFTER gender,
    ADD COLUMN current_job_group VARCHAR(30) NULL AFTER birth_year,
    ADD COLUMN target_job_group VARCHAR(30) NULL AFTER current_job_group,
    ADD COLUMN level INT NOT NULL DEFAULT 1 AFTER target_job_group;

-- 얻고 싶은 역량 (사용자당 여러 개). 탈퇴 시 함께 삭제
CREATE TABLE user_competencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    competency VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_user_competencies_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_competencies_user_id_competency UNIQUE (user_id, competency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
