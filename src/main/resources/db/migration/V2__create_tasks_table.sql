-- 할 일 (API_SPEC 5장, D-102~D-106)
-- date: 배정일(null이면 미완료인 동안 항상 노출), due_date: 마감일(표시·지연 계산용)
-- completed_date: 완료한 날(완료 토글 시 서버가 기록), memo: 할 일당 메모 1개(500자)
CREATE TABLE tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(20) NOT NULL,
    date DATE NULL,
    due_date DATE NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_date DATE NULL,
    memo VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_tasks_user_id_date (user_id, date),
    INDEX idx_tasks_user_id_completed_date (user_id, completed_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
