package com.unis.tilly.user.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

// V5: 임시 키워드로 저장된 값 정리 — 없는 직군은 null, 없는 역량은 삭제, 새 목록에 있는 값은 유지
@Testcontainers
class ProfileKeywordMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @Test
    void V5는_새_목록에_없는_직군과_역량만_정리한다() throws Exception {
        flyway("4").migrate();
        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO users (id, email, nickname, provider, provider_user_id, level,"
                    + " current_job_group, target_job_group, created_at, updated_at) VALUES"
                    + " (1, 'a@example.com', '옛코드', 'GOOGLE', 's1', 1, 'DEVELOPMENT', 'DATA', NOW(6), NOW(6)),"
                    + " (2, 'b@example.com', '새코드', 'GOOGLE', 's2', 1, 'MARKETING', 'DESIGN', NOW(6), NOW(6))");
            st.executeUpdate("INSERT INTO user_competencies (user_id, competency, created_at, updated_at) VALUES"
                    + " (1, 'COMMUNICATION', NOW(6), NOW(6)), (1, 'DSN_UX', NOW(6), NOW(6)),"
                    + " (2, 'PARTNERSHIP', NOW(6), NOW(6)), (2, 'DSN_TOOL', NOW(6), NOW(6))");
        }

        flyway(null).migrate();

        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            assertThat(jobGroups(st, 1)).isEqualTo("null/null");
            assertThat(jobGroups(st, 2)).isEqualTo("MARKETING/DESIGN");
            assertThat(competencies(st, 1)).isEqualTo("");
            assertThat(competencies(st, 2)).isEqualTo("DSN_TOOL,PARTNERSHIP");
        }
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private String jobGroups(Statement st, long userId) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT current_job_group, target_job_group FROM users WHERE id = " + userId)) {
            rs.next();
            return rs.getString(1) + "/" + rs.getString(2);
        }
    }

    private String competencies(Statement st, long userId) throws Exception {
        try (ResultSet rs = st.executeQuery("SELECT GROUP_CONCAT(competency ORDER BY competency) FROM user_competencies"
                + " WHERE user_id = " + userId)) {
            rs.next();
            String value = rs.getString(1);
            return value == null ? "" : value;
        }
    }
}
