-- 자동 질문 스케줄러 중복 실행 방지(ShedLock, D-138): 락 이름당 1행
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 스케줄러가 매분 "퇴근 시간이 지금 + 10분인 사용자"를 조회하므로 전체 조회를 피하기 위한 인덱스
CREATE INDEX idx_users_work_end_time ON users (work_end_time);
