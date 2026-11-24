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

// V7: 출생연도 → 나이. 기존 값은 2026 − 출생연도, 없던 값은 null, birth_year 컬럼은 삭제
@Testcontainers
class AgeMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("tilly")
            .withUsername("tilly")
            .withPassword("tilly");

    @Test
    void V7은_출생연도를_나이로_바꾸고_출생연도_컬럼을_지운다() throws Exception {
        flyway("6").migrate();
        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO users (id, email, nickname, provider, provider_user_id, level, birth_year,"
                    + " created_at, updated_at) VALUES"
                    + " (1, 'a@example.com', '출생연도', 'GOOGLE', 's1', 1, 2001, NOW(6), NOW(6)),"
                    + " (2, 'b@example.com', '없음', 'GOOGLE', 's2', 1, NULL, NOW(6), NOW(6))");
        }

        flyway(null).migrate();

        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            assertThat(age(st, 1)).isEqualTo("25");
            assertThat(age(st, 2)).isNull();
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.columns"
                    + " WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'birth_year'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
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

    private String age(Statement st, long userId) throws Exception {
        try (ResultSet rs = st.executeQuery("SELECT age FROM users WHERE id = " + userId)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
