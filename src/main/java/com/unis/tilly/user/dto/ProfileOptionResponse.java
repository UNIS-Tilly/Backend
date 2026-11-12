package com.unis.tilly.user.dto;

import com.unis.tilly.user.enums.Competency;
import com.unis.tilly.user.enums.CompetencyType;
import com.unis.tilly.user.enums.Gender;
import com.unis.tilly.user.enums.JobGroup;

import java.util.Arrays;
import java.util.List;

// 4.3 프로필 선택지. 서버 ENUM에서 만든다 (D-99, 목록은 [임시])
public record ProfileOptionResponse(List<Option> genders, List<Option> jobGroups,
                                    List<CompetencyOption> competencies, SelectionRules selectionRules) {

    public static ProfileOptionResponse create() {
        return new ProfileOptionResponse(
                Arrays.stream(Gender.values()).map(g -> new Option(g.name(), g.getLabel())).toList(),
                Arrays.stream(JobGroup.values()).map(j -> new Option(j.name(), j.getLabel())).toList(),
                Arrays.stream(Competency.values())
                        .map(c -> new CompetencyOption(c.name(), c.getLabel(), c.getType(), c.getJobGroup()))
                        .toList(),
                new SelectionRules(Competency.COMMON_MIN, Competency.JOB_MIN));
    }

    public record Option(String code, String label) {
    }

    public record CompetencyOption(String code, String label, CompetencyType type, JobGroup jobGroup) {
    }

    public record SelectionRules(int commonMin, int jobMin) {
    }
}
