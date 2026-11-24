-- 할 일 마감일을 날짜 → 일시(분 단위, Asia/Seoul)로 변경 (API_SPEC 5.0, 2026-11-12 사용자 결정)
-- 기존 마감일(시간 없음)은 그날 끝까지 마감으로 보고 23:59:00으로 채운다.
ALTER TABLE tasks MODIFY due_date DATETIME NULL;

UPDATE tasks
SET due_date = TIMESTAMP(DATE(due_date), '23:59:00')
WHERE due_date IS NOT NULL;
