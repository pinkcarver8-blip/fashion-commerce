package io.github.pinkcarver8blip.querydslsmoke;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 컨테이너를 Spring 빈으로 둔다. 컨텍스트가 닫힌 뒤에 컨테이너가 멈추므로
 * 종료할 때 테이블 삭제(create-drop)가 끊긴 연결을 기다리지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer("mysql:8.4");
	}
}
