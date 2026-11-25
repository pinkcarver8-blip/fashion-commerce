package io.github.pinkcarver8_blip.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * R0-08 시나리오 11(ADR-010): DB 연결·JSON의 시간대를 앱 설정이 UTC로 강제한다.
 *
 * <p>설정을 "읽어 보는" 대신 서울 시간(+09:00)을 일부러 넣어 보고 결과가 UTC인지 본다. 설정을 지우면 실패해야 의미가 있다.
 *
 * <ul>
 *   <li>DB 서버는 +09:00으로 띄우고, JVM 기본 시간대도 잠깐 서울로 바꾼 채 실제 application.yml의 연결 주소로 새로 붙는다.
 *   <li>JSON은 +09:00 시각을 넣어 UTC로 바뀌어 나오는지 본다.
 *   <li>JVM 시간대는 시험 JVM 설정(build.gradle)만 확인한다. 실행 이미지의 JVM은 CI `stack` 작업이 확인한다.
 * </ul>
 */
@SpringBootTest
@Testcontainers
class TimeZoneConfigurationTest {

    /** compose와 같은 판·지문(A5-12). */
    private static final DockerImageName MYSQL_IMAGE =
            DockerImageName.parse(
                            "mysql:8.4.11@sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364")
                    .asCompatibleSubstituteFor("mysql");

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer(MYSQL_IMAGE)
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

    @Autowired Environment environment;

    @Autowired EntityManagerFactory entityManagerFactory;

    @Autowired JsonMapper jsonMapper;

    @Test
    void R0_08_11_시험_JVM_기본_시간대는_UTC다() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void R0_08_11_DB_서버와_JVM이_서울_시간이어도_앱_연결은_UTC다() throws Exception {
        String url = environment.getRequiredProperty("spring.datasource.url");
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        try (Connection connection =
                        DriverManager.getConnection(url, MYSQL.getUsername(), MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            assertThat(queryString(statement, "SELECT @@global.time_zone"))
                    .as("시험 전제: DB 서버 기본 시간대는 +09:00")
                    .isEqualTo("+09:00");
            assertThat(
                            queryString(
                                    statement,
                                    "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW())"))
                    .as("연결 시간대가 UTC면 NOW()와 UTC_TIMESTAMP()가 같다")
                    .isEqualTo("0");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void R0_08_11_Hibernate_저장_시간대_설정은_UTC다() {
        assertThat(entityManagerFactory.getProperties().get("hibernate.jdbc.time_zone"))
                .hasToString("UTC");
    }

    @Test
    void R0_08_11_서울_시각을_JSON으로_내보내면_UTC로_바뀐다() {
        OffsetDateTime seoulNine = OffsetDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9));

        String json = jsonMapper.writeValueAsString(seoulNine);

        assertThat(json).isIn("\"2026-01-01T00:00:00Z\"", "\"2026-01-01T00:00:00+00:00\"");
    }

    private static String queryString(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
