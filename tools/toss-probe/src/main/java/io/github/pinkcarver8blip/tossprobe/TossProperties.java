package io.github.pinkcarver8blip.tossprobe;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스 테스트 키. 시작할 때 형식만 검사하고, 값은 어디에도 출력하지 않는다.
 * <ul>
 * <li>clientKey·secretKey: 결제위젯 키(필수). 계약 전에는 문서 공용 상점(tgen_docs) 키다.</li>
 * <li>apiClientKey·apiSecretKey: API 개별 연동 키(선택). 내 테스트 상점 키라 웹훅을 받을 수 있다.</li>
 * </ul>
 */
@ConfigurationProperties("toss")
public record TossProperties(String clientKey, String secretKey, String apiClientKey, String apiSecretKey) {

	public TossProperties {
		require(clientKey, "TOSS_CLIENT_KEY", "test_gck_");
		require(secretKey, "TOSS_SECRET_KEY", "test_gsk_");
		boolean noApiClient = apiClientKey == null || apiClientKey.isBlank();
		boolean noApiSecret = apiSecretKey == null || apiSecretKey.isBlank();
		if (noApiClient != noApiSecret) {
			throw new IllegalStateException("TOSS_API_CLIENT_KEY와 TOSS_API_SECRET_KEY는 둘 다 넣거나 둘 다 비운다");
		}
		if (!noApiClient) {
			require(apiClientKey, "TOSS_API_CLIENT_KEY", "test_ck_");
			require(apiSecretKey, "TOSS_API_SECRET_KEY", "test_sk_");
		}
	}

	public boolean hasApiKeys() {
		return apiClientKey != null && !apiClientKey.isBlank();
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
					name + "가 " + prefix + "로 시작하지 않는다(키 종류가 맞는지, 따옴표·공백이 붙지 않았는지 확인)");
		}
	}

	@Override
	public String toString() {
		return "TossProperties[clientKey=" + clientKey.substring(0, 9) + "***, secretKey=***, apiKeys="
				+ (hasApiKeys() ? "있음" : "없음") + "]";
	}
}
