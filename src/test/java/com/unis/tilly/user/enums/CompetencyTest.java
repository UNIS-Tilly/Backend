package com.unis.tilly.user.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// 기획 확정 키워드(2026-11-12) 무결성: 모든 직군이 선택 규칙(직군 역량 2개 이상)을 만족할 수 있어야 한다 (D-98)
class CompetencyTest {

    @Test
    void 공통_역량은_9개이고_직군이_없다() {
        assertThat(Arrays.stream(Competency.values()).filter(c -> c.getType() == CompetencyType.COMMON))
                .hasSize(9)
                .allSatisfy(c -> assertThat(c.getJobGroup()).isNull());
    }

    @Test
    void 모든_직군은_최소_선택_개수_이상의_역량을_가진다() {
        for (JobGroup jobGroup : JobGroup.values()) {
            long count = Arrays.stream(Competency.values()).filter(c -> c.getJobGroup() == jobGroup).count();
            assertThat(count).as(jobGroup.name()).isGreaterThanOrEqualTo(Competency.JOB_MIN);
        }
    }

    @Test
    void 표시명과_정의가_비어_있지_않고_직군_역량은_직군_접두어로_구분된다() {
        assertThat(JobGroup.values()).hasSize(13).allSatisfy(j -> assertThat(j.getLabel()).isNotBlank());
        for (Competency c : Competency.values()) {
            assertThat(c.getLabel()).as(c.name()).isNotBlank();
            assertThat(c.getDescription()).as(c.name()).isNotBlank();
            assertThat(c.name().length()).as(c.name()).isLessThanOrEqualTo(40); // user_competencies.competency 길이
            if (c.getType() == CompetencyType.JOB) {
                assertThat(c.getJobGroup()).as(c.name()).isNotNull();
                assertThat(c.name()).as(c.name()).contains("_");
            }
        }
    }
}
