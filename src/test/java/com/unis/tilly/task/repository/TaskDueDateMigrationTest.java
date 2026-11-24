package com.unis.tilly.task.repository;

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

// V6: 마감일(DATE) → 마감 일시(DATETIME). 기존 마감일은 그날 23:59:00, 없던 마감은 그대로 null
@Testcontainers
class TaskDueDateMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @Test
    void V6는_기존_마감일을_그날_23시59분으로_채운다() throws Exception {
        flyway("5").migrate();
        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO users (id, email, nickname, provider, provider_user_id, level,"
                    + " created_at, updated_at) VALUES (1, 'a@example.com', '사용자', 'GOOGLE', 's1', 1, NOW(6), NOW(6))");
            st.executeUpdate("INSERT INTO tasks (id, user_id, title, date, due_date, completed, created_at, updated_at)"
                    + " VALUES (1, 1, '마감 있음', '2026-10-21', '2026-10-23', FALSE, NOW(6), NOW(6)),"
                    + " (2, 1, '마감 없음', '2026-10-21', NULL, FALSE, NOW(6), NOW(6))");
        }

        flyway(null).migrate();

        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            assertThat(dueDate(st, 1)).isEqualTo("2026-10-23 23:59:00");
            assertThat(dueDate(st, 2)).isNull();
            // 시각까지 저장된다
            st.executeUpdate("UPDATE tasks SET due_date = '2026-10-24 18:30:00' WHERE id = 2");
            assertThat(dueDate(st, 2)).isEqualTo("2026-10-24 18:30:00");
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

    private String dueDate(Statement st, long taskId) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT DATE_FORMAT(due_date, '%Y-%m-%d %H:%i:%s') FROM tasks WHERE id = " + taskId)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
