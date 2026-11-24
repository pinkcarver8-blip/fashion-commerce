package io.github.pinkcarver8_blip.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * R0-08 시나리오 11(ADR-010): JVM·DB 연결·Hibernate·JSON의 시간대가 UTC다.
 *
 * <p>DB 서버는 일부러 서울 시간(+09:00)으로 띄운다. 앱 설정이 UTC를 강제하지 않으면 DB 연결 시간대가 +09:00이 되어 실패한다. 연결 주소는 실제
 * application.yml의 틀을 그대로 쓰고 호스트·포트·이름만 넘긴다.
 */
@SpringBootTest
@Testcontainers
class TimeZoneConfigurationTest {

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4.11")
                    .withEnv("TZ", "Asia/Seoul")
                    .withCommand("--default-time-zone=+09:00");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", () -> MYSQL.getMappedPort(3306));
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USERNAME", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        // Redis는 연결을 처음 쓸 때 붙으므로 이 시험에서는 띄우지 않는다.
        registry.add("REDIS_HOST", () -> "localhost");
        registry.add("REDIS_PASSWORD", () -> "unused");
    }

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired EntityManagerFactory entityManagerFactory;

    @Autowired JsonMapper jsonMapper;

    @Test
    void R0_08_11_JVM_기본_시간대는_UTC다() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void R0_08_11_DB_서버가_서울_시간이어도_연결_시간대는_UTC다() {
        assertThat(jdbcTemplate.queryForObject("SELECT @@global.time_zone", String.class))
                .as("시험 전제: DB 서버 기본 시간대는 +09:00")
                .isEqualTo("+09:00");
        Long offsetSeconds =
                jdbcTemplate.queryForObject(
                        "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW())", Long.class);
        assertThat(offsetSeconds).isZero();
    }

    @Test
    void R0_08_11_Hibernate가_저장하는_시간대는_UTC다() {
        assertThat(entityManagerFactory.getProperties().get("hibernate.jdbc.time_zone"))
                .hasToString("UTC");
    }

    @Test
    void R0_08_11_JSON_응답의_시간대는_UTC다() {
        assertThat(jsonMapper.serializationConfig().getTimeZone().getID()).isEqualTo("UTC");
    }
}
