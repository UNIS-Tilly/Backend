-- 회원 프로필: 출생연도 → 나이 (API_SPEC 4.2, 2026-11-12 사용자 결정)
-- 나이는 입력받은 값을 그대로 저장한다. 기존 출생연도는 2026 − 출생연도로 변환한다(개발 데이터).
ALTER TABLE users ADD COLUMN age INT NULL AFTER gender;

UPDATE users
SET age = 2026 - birth_year
WHERE birth_year IS NOT NULL;

ALTER TABLE users DROP COLUMN birth_year;
