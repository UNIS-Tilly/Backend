package com.unis.tilly.insight.service;

import com.unis.tilly.ai.dto.AiTaskInput;
import com.unis.tilly.ai.dto.WeeklyInsightInput;
import com.unis.tilly.insight.dto.InsightSnapshot;
import com.unis.tilly.insight.entity.WeeklyInsight;
import com.unis.tilly.record.service.RecordService;
import com.unis.tilly.task.entity.Task;
import com.unis.tilly.task.service.TaskService;
import com.unis.tilly.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// 한 주의 기록을 모아 요청 가능 여부(eligibility)와 AI 입력·스냅샷을 만든다 (D-112, D-126, D-128, D-133, D-135, D-136).
// - 주간 달성 집계는 배정일 기준(그날 배정된 할 일 수 / 그 중 완료한 수). 완료일은 쓰지 않는다
// - AI 입력·요약 수치(메모·회고·답변)는 퇴근 완료(COMPLETED)한 날의 기록만 반영한다 (API_SPEC 8장 4번)
// - 지난 주 비교 자료는 지난 주에 배정된 할 일이 하나라도 있을 때만 준다(없으면 null → 비교 문구 금지, D-112)
@Service
@RequiredArgsConstructor
public class InsightInputAssembler {

    public static final int REQUIRED_DAYS = 3;   // D-51

    private final TaskService taskService;
    private final RecordService recordService;
    private final UserService userService;
    private final InsightSnapshots snapshots;

    // 그 주에 모인 기록. 요청 가능 여부 판단과 AI 입력 생성에 같이 쓴다
    public WeekData collect(Long userId, LocalDate weekStart) {
        LocalDate weekEnd = WeekRules.weekEnd(weekStart);
        List<Task> weekTasks = taskService.getTasksBetween(userId, weekStart, weekEnd);
        List<RecordService.CompletedDay> completedDays = recordService.getCompletedDays(userId, weekStart, weekEnd);
        return new WeekData(weekStart, weekEnd, weekTasks, completedDays);
    }

    public Assembled assemble(Long userId, WeekData data) {
        LocalDate weekStart = data.weekStart();
        Map<LocalDate, List<Task>> tasksByDate = data.weekTasks().stream()
                .collect(Collectors.groupingBy(Task::getDate));

        InsightSnapshot.Achievement achievement = achievement(weekStart, tasksByDate);
        List<InsightSnapshot.FlowWeek> flow = flow(userId, weekStart);

        WeeklyInsightInput.WeekStats stats =
                new WeeklyInsightInput.WeekStats(achievement.totalCount(), achievement.completedCount());
        WeeklyInsightInput.WeekStats previous = previousWeekStats(userId, weekStart);

        List<WeeklyInsightInput.DayInput> days = data.completedDays().stream()
                .map(day -> new WeeklyInsightInput.DayInput(
                        day.date(),
                        tasksByDate.getOrDefault(day.date(), List.of()).stream()
                                .map(t -> new AiTaskInput(t.getTitle(), t.isCompleted(), t.getMemo()))
                                .toList(),
                        day.answers().stream()
                                .map(a -> new WeeklyInsightInput.AnswerInput(a.question(), a.answer()))
                                .toList(),
                        day.reflection()))
                .toList();

        WeeklyInsight.Summary summary = new WeeklyInsight.Summary(
                data.recordedDays(), data.memoCount(), data.reflectionCount(), data.answerCount(),
                achievement.totalCount(), achievement.completedCount());
        WeeklyInsightInput input = new WeeklyInsightInput(weekStart, data.weekEnd(), WeekRules.label(weekStart),
                stats, previous, data.insufficientContent(), days, profile(userId));
        return new Assembled(input, summary, snapshots.write(achievement), snapshots.write(flow));
    }

    private InsightSnapshot.Achievement achievement(LocalDate weekStart, Map<LocalDate, List<Task>> tasksByDate) {
        List<InsightSnapshot.Day> days = weekStart.datesUntil(weekStart.plusDays(7)).map(date -> {
            List<Task> tasks = tasksByDate.getOrDefault(date, List.of());
            return new InsightSnapshot.Day(date.toString(), dayOfWeek(date.getDayOfWeek()),
                    (int) tasks.stream().filter(Task::isCompleted).count(), tasks.size());
        }).toList();
        return new InsightSnapshot.Achievement(
                days.stream().mapToInt(InsightSnapshot.Day::totalCount).sum(),
                days.stream().mapToInt(InsightSnapshot.Day::completedCount).sum(),
                days);
    }

    // 같은 달 1주차~해당 주차 (주 경계·집계 정의는 weeklyAchievement와 같다)
    private List<InsightSnapshot.FlowWeek> flow(Long userId, LocalDate weekStart) {
        List<LocalDate> weeks = WeekRules.monthWeeksUpTo(weekStart);
        List<Task> tasks = taskService.getTasksBetween(userId, weeks.get(0), WeekRules.weekEnd(weekStart));
        return weeks.stream().map(monday -> {
            LocalDate sunday = WeekRules.weekEnd(monday);
            List<Task> inWeek = tasks.stream()
                    .filter(t -> !t.getDate().isBefore(monday) && !t.getDate().isAfter(sunday))
                    .toList();
            return new InsightSnapshot.FlowWeek(WeekRules.label(monday), monday.toString(), sunday.toString(),
                    (int) inWeek.stream().filter(Task::isCompleted).count(), inWeek.size());
        }).toList();
    }

    private WeeklyInsightInput.WeekStats previousWeekStats(Long userId, LocalDate weekStart) {
        LocalDate previousStart = weekStart.minusWeeks(1);
        List<Task> tasks = taskService.getTasksBetween(userId, previousStart, WeekRules.weekEnd(previousStart));
        if (tasks.isEmpty()) {
            return null;
        }
        return new WeeklyInsightInput.WeekStats(tasks.size(), (int) tasks.stream().filter(Task::isCompleted).count());
    }

    // D-133: 희망 직군과 선택 역량(코드·표시명·정의)만. 닉네임·이메일·성별·나이는 보내지 않는다
    private WeeklyInsightInput.Profile profile(Long userId) {
        UserService.UserProfile profile = userService.getProfile(userId);
        var target = profile.user().getTargetJobGroup();
        return new WeeklyInsightInput.Profile(
                target == null ? null : target.name(),
                target == null ? null : target.getLabel(),
                profile.competencies().stream()
                        .map(c -> new WeeklyInsightInput.CompetencyInfo(c.name(), c.getLabel(), c.getDescription()))
                        .toList());
    }

    private String dayOfWeek(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);   // MON ~ SUN
    }

    // 한 주에 모인 기록과 파생 수치
    public record WeekData(LocalDate weekStart, LocalDate weekEnd, List<Task> weekTasks,
                           List<RecordService.CompletedDay> completedDays) {

        public int recordedDays() {
            return completedDays.size();
        }

        // 퇴근 완료한 날에 배정된 할 일 중 메모가 있는 수
        public int memoCount() {
            List<LocalDate> dates = completedDays.stream().map(RecordService.CompletedDay::date).toList();
            return (int) weekTasks.stream()
                    .filter(t -> dates.contains(t.getDate()))
                    .filter(t -> t.getMemo() != null && !t.getMemo().isBlank())
                    .count();
        }

        public int reflectionCount() {
            return (int) completedDays.stream().filter(d -> d.reflection() != null).count();
        }

        public int answerCount() {
            return completedDays.stream().mapToInt(d -> d.answers().size()).sum();
        }

        // 메모·회고·답변이 하나도 없어 할 일 제목만으로 분석해야 하는 주 (경고 INSUFFICIENT_CONTENT, D-54)
        public boolean insufficientContent() {
            return memoCount() == 0 && reflectionCount() == 0 && answerCount() == 0;
        }

        // D-136: 할 일·회고·메모·답변이 모두 없으면 분석할 내용이 없다
        public boolean hasContent() {
            return !weekTasks.isEmpty() || reflectionCount() > 0 || answerCount() > 0;
        }

        // D-51, D-136: 퇴근 완료 3일 이상이고 분석할 내용이 있어야 한다
        public boolean eligible() {
            return recordedDays() >= REQUIRED_DAYS && hasContent();
        }
    }

    public record Assembled(WeeklyInsightInput input, WeeklyInsight.Summary summary,
                            String achievementJson, String flowJson) {
    }
}
