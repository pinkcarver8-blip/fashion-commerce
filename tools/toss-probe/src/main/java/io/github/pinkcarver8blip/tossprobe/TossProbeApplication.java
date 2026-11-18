package io.github.pinkcarver8blip.tossprobe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * R0-03 토스 외부 동작 확인 도구. 제품 코드가 아니다.
 * 실제 응답을 out/에 원문으로 남기고(커밋 안 함), 민감값을 바꾼 사본만 tools/pg-stub/responses/에 둔다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TossProbeApplication {

	public static void main(String[] args) {
		SpringApplication.run(TossProbeApplication.class, args);
	}

}
