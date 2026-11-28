-- 재직 직군의 세부 직군 (사용자 직접 입력, 선택 항목, 30자 이하)
ALTER TABLE users
    ADD COLUMN current_job_detail VARCHAR(30) NULL AFTER current_job_group;
