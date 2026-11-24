-- 할 일은 배정일 하루에만 속한다(이월 없음). 배정일 없던 기존 할 일은 생성일로 채우고 NOT NULL로 바꾼다.
-- 완료일(completed_date)은 그대로 두고 노출 기준에서만 뺀다. 마감 일시(due_date)는 표시용이라 변경 없음.
UPDATE tasks
SET date = DATE(created_at)
WHERE date IS NULL;

ALTER TABLE tasks MODIFY date DATE NOT NULL;
