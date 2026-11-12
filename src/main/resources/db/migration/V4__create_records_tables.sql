-- 하루 기록 (API_SPEC 6장, D-95, D-107, D-108). 회고 컬럼 없음
-- status: NOT_STARTED(질문 생성 등으로 행만 생긴 경우) / DRAFT(수동 임시저장) / COMPLETED(퇴근 완료)
-- 행이 없는 날도 응답에서는 NOT_STARTED로 표현한다
CREATE TABLE daily_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_daily_records_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_daily_records_user_id_date UNIQUE (user_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 하루 질문(AI 확장 질문, 최대 3개)과 답변(1,000자)
CREATE TABLE record_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    question_order INT NOT NULL,
    text TEXT NOT NULL,
    answer VARCHAR(1000) NULL,
    skipped BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_record_questions_record FOREIGN KEY (record_id) REFERENCES daily_records (id) ON DELETE CASCADE,
    CONSTRAINT uk_record_questions_record_id_order UNIQUE (record_id, question_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
