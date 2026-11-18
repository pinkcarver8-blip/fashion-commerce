package io.github.pinkcarver8blip.tossprobe;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스 결제위젯 테스트 키. 시작할 때 형식만 검사하고, 값은 어디에도 출력하지 않는다.
 */
@ConfigurationProperties("toss")
public record TossProperties(String clientKey, String secretKey) {

	public TossProperties {
		require(clientKey, "TOSS_CLIENT_KEY", "test_gck_");
		require(secretKey, "TOSS_SECRET_KEY", "test_gsk_");
	}

	private static void require(String value, String name, String prefix) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + "가 비어 있다. 저장소 루트 .env에 넣었는지 확인한다");
		}
		if (value.startsWith("live_")) {
			throw new IllegalStateException(name + "가 운영 키다. 테스트 키만 쓴다(E44)");
		}
		if (!value.startsWith(prefix)) {
			throw new IllegalStateException(
					name + "가 " + prefix + "로 시작하지 않는다(결제위젯 테스트 키인지, 따옴표·공백이 붙지 않았는지 확인)");
		}
	}

	@Override
	public String toString() {
		return "TossProperties[clientKey=" + clientKey.substring(0, 9) + "***, secretKey=***]";
	}
}
