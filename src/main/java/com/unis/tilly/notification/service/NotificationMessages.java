package com.unis.tilly.notification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 13.0 알림 문구 (서버가 만들어 저장). 확정 문구가 바뀌면 이 클래스만 고친다 (PLANNING_NOTICE P-22)
final class NotificationMessages {

    static final String DUE_SOON_TITLE = "마감이 곧 다가와요";
    static final String INSIGHT_TITLE = "주간 인사이트가 도착했어요";
    static final String RECORD_TITLE = "오늘 기록을 아직 남기지 않았어요";
    static final String RECORD_BODY = "퇴근 전 5분만 투자해 오늘의 경험을 기록해 볼까요?";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일");

    private NotificationMessages() {
    }

    // 예: '주간 회의 자료 정리'가 오늘 11:00에 마감이에요.
    static String dueSoonBody(String taskTitle, LocalDateTime dueDate, LocalDate today) {
        return "'" + taskTitle + "'" + subjectParticle(taskTitle) + " " + dayWord(dueDate.toLocalDate(), today) + " "
                + HH_MM.format(dueDate) + "에 마감이에요.";
    }

    static String insightBody(String weekLabel) {
        return weekLabel + " 분석 결과를 확인해 보세요.";
    }

    // 마지막 글자가 받침 없는 한글이면 "가", 받침 있는 한글이면 "이", 한글이 아니면 "이(가)"
    static String subjectParticle(String text) {
        if (text == null || text.isEmpty()) {
            return "이(가)";
        }
        char last = text.charAt(text.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "이(가)";
        }
        return (last - 0xAC00) % 28 == 0 ? "가" : "이";
    }

    private static String dayWord(LocalDate dueDay, LocalDate today) {
        if (dueDay.equals(today)) {
            return "오늘";
        }
        if (dueDay.equals(today.plusDays(1))) {
            return "내일";
        }
        return MONTH_DAY.format(dueDay);
    }
}
