-- 알림 (API_SPEC 13장, D-152): 헤더 종 아이콘 목록. 문구(title·body)는 만들 때 저장하고 바꾸지 않는다.
-- target_*: 클릭 시 이동 대상(할 일·인사이트·기록 날짜), read_at이 NULL이면 안 읽음
-- dedupe_key: 같은 알림을 두 번 만들지 않기 위한 키 (스케줄러 재실행·다중 인스턴스 대비)
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(255) NOT NULL,
    target_type VARCHAR(20) NULL,
    target_id BIGINT NULL,
    target_date DATE NULL,
    dedupe_key VARCHAR(100) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_notifications_user_dedupe UNIQUE (user_id, dedupe_key),
    INDEX idx_notifications_user_created (user_id, created_at),
    INDEX idx_notifications_user_read (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 마감 임박 알림 스케줄러가 매분 "마감이 곧인 미완료 할 일"을 조회하므로 마감 일시 인덱스가 필요하다
CREATE INDEX idx_tasks_due_date ON tasks (due_date);
