package io.github.pinkcarver8blip.tossprobe;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 토스 API 호출. 응답을 해석하지 않고 상태 코드·본문·주요 헤더를 그대로 기록한다.
 */
@Component
public class TossClient {

	private static final String BASE = "https://api.tosspayments.com";
	private static final List<String> KEPT_HEADERS = List.of("Content-Type", "Date", "Retry-After");

	public record Result(int status, String body, String file, long elapsedMs) {
	}

	private final TossProperties toss;
	private final Recorder recorder;

	public TossClient(TossProperties toss, Recorder recorder) {
		this.toss = toss;
		this.recorder = recorder;
	}

	/** api: true면 API 개별 연동 키(결제창 주문), false면 결제위젯 키로 호출한다. */
	public Result getPayment(boolean api, String paymentKey, Integer timeoutMs) {
		return send(api, "payment-get", HttpMethod.GET, "/v1/payments/" + enc(paymentKey), null, null, timeoutMs);
	}

	public Result confirm(boolean api, String paymentKey, String orderId, long amount, String idempotencyKey,
			Integer timeoutMs) {
		String body = "{\"paymentKey\":" + Recorder.q(paymentKey) + ",\"orderId\":" + Recorder.q(orderId)
				+ ",\"amount\":" + amount + "}";
		return send(api, "confirm", HttpMethod.POST, "/v1/payments/confirm", body, idempotencyKey, timeoutMs);
	}

	public Result cancel(boolean api, String paymentKey, String reason, Long cancelAmount, String idempotencyKey,
			Integer timeoutMs) {
		String body = "{\"cancelReason\":" + Recorder.q(reason)
				+ (cancelAmount == null ? "" : ",\"cancelAmount\":" + cancelAmount) + "}";
		return send(api, "cancel", HttpMethod.POST, "/v1/payments/" + enc(paymentKey) + "/cancel", body,
				idempotencyKey, timeoutMs);
	}

	public Result transactions(boolean api, String startDate, String endDate) {
		return send(api, "transactions", HttpMethod.GET,
				"/v1/transactions?startDate=" + enc(startDate) + "&endDate=" + enc(endDate), null, null,
				60000); // 문서: 거래 조회는 최대 60초
	}

	private Result send(boolean api, String label, HttpMethod method, String path, String body,
			String idempotencyKey, Integer timeoutMs) {
		if (api && !toss.hasApiKeys()) {
			String file = recorder.call(label, Map.of("keys", "api"), -1, 0, Map.of(), "API 개별 연동 키 없음");
			return new Result(-1, "API 개별 연동 키 없음", file, 0);
		}
		Map<String, String> req = new LinkedHashMap<>();
		req.put("keys", api ? "api" : "widget");
		req.put("method", method.name());
		req.put("path", path);
		req.put("idempotencyKey", idempotencyKey);
		req.put("readTimeoutMs", timeoutMs == null ? "15000" : timeoutMs.toString());
		req.put("body", body);

		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofMillis(timeoutMs == null ? 15000 : timeoutMs));
		RestClient client = RestClient.builder().requestFactory(factory).build();

		String secret = api ? toss.apiSecretKey() : toss.secretKey();
		String basic = Base64.getEncoder().encodeToString((secret + ":").getBytes(StandardCharsets.UTF_8));
		long start = System.nanoTime();
		try {
			// 이미 인코딩한 경로라 URI로 넘긴다(문자열로 넘기면 %가 다시 인코딩된다)
			var spec = client.method(method).uri(URI.create(BASE + path)).header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
			if (idempotencyKey != null) {
				spec = spec.header("Idempotency-Key", idempotencyKey);
			}
			if (body != null) {
				spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
			}
			var entity = spec.retrieve().onStatus(s -> true, (rq, rs) -> {
			}).toEntity(String.class);
			long elapsed = (System.nanoTime() - start) / 1_000_000;
			Map<String, String> headers = new LinkedHashMap<>();
			for (String h : KEPT_HEADERS) {
				String v = entity.getHeaders().getFirst(h);
				if (v != null) {
					headers.put(h, v);
				}
			}
			int status = entity.getStatusCode().value();
			String file = recorder.call(label, req, status, elapsed, headers, entity.getBody());
			return new Result(status, entity.getBody(), file, elapsed);
		}
		catch (RuntimeException e) {
			long elapsed = (System.nanoTime() - start) / 1_000_000;
			// 예외 메시지에 요청 내용이 섞일 수 있어 종류와 원인 종류만 남긴다
			Throwable root = e;
			while (root.getCause() != null) {
				root = root.getCause();
			}
			String summary = e.getClass().getSimpleName() + " / " + root.getClass().getSimpleName();
			String file = recorder.call(label, req, -1, elapsed, Map.of(), summary);
			return new Result(-1, summary, file, elapsed);
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
