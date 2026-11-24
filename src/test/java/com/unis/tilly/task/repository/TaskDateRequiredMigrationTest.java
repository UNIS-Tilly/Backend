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
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V8: 할 일 배정일 필수. 배정일 없던 기존 할 일은 생성일로 채우고, 이후 NULL은 DB가 거절한다
@Testcontainers
class TaskDateRequiredMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @Test
    void V8은_배정일이_없던_할_일을_생성일로_채우고_NULL을_막는다() throws Exception {
        flyway("7").migrate();
        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO users (id, email, nickname, provider, provider_user_id, level,"
                    + " created_at, updated_at) VALUES (1, 'a@example.com', '사용자', 'GOOGLE', 's1', 1, NOW(6), NOW(6))");
            st.executeUpdate("INSERT INTO tasks (id, user_id, title, date, completed, created_at, updated_at) VALUES"
                    + " (1, 1, '배정일 있음', '2026-10-21', FALSE, '2026-10-15 09:00:00', '2026-10-15 09:00:00'),"
                    + " (2, 1, '배정일 없음', NULL, FALSE, '2026-10-18 23:30:00', '2026-10-18 23:30:00')");
        }

        flyway(null).migrate();

        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            assertThat(date(st, 1)).isEqualTo("2026-10-21");   // 있던 값은 그대로
            assertThat(date(st, 2)).isEqualTo("2026-10-18");   // 생성일로 채움
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO tasks (user_id, title, date, completed, created_at, updated_at)"
                            + " VALUES (1, '날짜 없음', NULL, FALSE, NOW(6), NOW(6))"))
                    .isInstanceOf(SQLException.class);
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

    private String date(Statement st, long taskId) throws Exception {
        try (ResultSet rs = st.executeQuery("SELECT DATE_FORMAT(date, '%Y-%m-%d') FROM tasks WHERE id = " + taskId)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
