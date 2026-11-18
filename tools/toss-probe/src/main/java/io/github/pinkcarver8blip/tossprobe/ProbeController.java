package io.github.pinkcarver8blip.tossprobe;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검증용 호출. 시크릿 키를 쓰므로 LocalOnlyFilter가 이 PC에서 직접 온 요청만 통과시킨다.
 * 응답 본문 대신 기록 파일 이름과 상태만 돌려준다(원문은 out/calls).
 */
@RestController
@RequestMapping("/probe")
public class ProbeController {

	private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");
	private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_0-9]+)\"");

	/** 상태가 바뀔 때까지 주기적으로 조회한다(30분 만료 기산점 확인용). */
	record Watch(String paymentKey, Duration interval, Instant until, String lastStatus, Instant nextAt) {
	}

	private final TossClient client;
	private final ShopController shop;
	private final Recorder recorder;
	private final WebhookController webhook;
	private final Map<String, Watch> watches = new ConcurrentHashMap<>();

	public ProbeController(TossClient client, ShopController shop, Recorder recorder, WebhookController webhook) {
		this.client = client;
		this.shop = shop;
		this.recorder = recorder;
		this.webhook = webhook;
	}

	@GetMapping("/orders")
	List<Map<String, Object>> orders() {
		return shop.orders().values().stream().sorted((a, b) -> a.createdAt.compareTo(b.createdAt)).map(o -> {
			Map<String, Object> m = ShopController.view(o);
			m.put("createdAt", o.createdAt.toString());
			m.put("authAt", o.authAt == null ? null : o.authAt.toString());
			m.put("hasPaymentKey", o.paymentKey != null);
			m.put("redirectedAmount", o.redirectedAmount);
			return m;
		}).toList();
	}

	@GetMapping("/payments/{orderId}")
	Map<String, Object> getPayment(@PathVariable String orderId, @RequestParam(required = false) Integer timeoutMs,
			@RequestParam(required = false) String paymentKey) {
		return summary(client.getPayment(keyOf(orderId, paymentKey), timeoutMs));
	}

	/**
	 * idem: 생략하면 confirm-{orderId}, "none"이면 헤더 없음. amount: 생략하면 저장한 금액.
	 */
	@PostMapping("/confirm/{orderId}")
	Map<String, Object> confirm(@PathVariable String orderId, @RequestParam(required = false) String idem,
			@RequestParam(required = false) Long amount, @RequestParam(required = false) Integer timeoutMs) {
		var order = shop.orders().get(orderId);
		if (order == null || order.paymentKey == null) {
			return Map.of("error", "주문이 없거나 아직 인증 결과(paymentKey)가 없다");
		}
		String key = idem == null ? "confirm-" + orderId : "none".equals(idem) ? null : idem;
		return summary(
				client.confirm(order.paymentKey, orderId, amount == null ? order.amount : amount, key, timeoutMs));
	}

	@PostMapping("/cancel/{orderId}")
	Map<String, Object> cancel(@PathVariable String orderId, @RequestParam(required = false) String idem,
			@RequestParam(defaultValue = "R0-03 확인") String reason, @RequestParam(required = false) Long amount,
			@RequestParam(required = false) Integer timeoutMs, @RequestParam(required = false) String paymentKey) {
		String key = idem == null ? "cancel-" + orderId : "none".equals(idem) ? null : idem;
		return summary(client.cancel(keyOf(orderId, paymentKey), reason, amount, key, timeoutMs));
	}

	@GetMapping("/transactions")
	Map<String, Object> transactions(@RequestParam String start, @RequestParam String end) {
		return summary(client.transactions(start, end));
	}

	@PostMapping("/watch/{orderId}")
	Map<String, Object> watch(@PathVariable String orderId, @RequestParam(defaultValue = "60") int intervalSec,
			@RequestParam(defaultValue = "45") int maxMin) {
		String key = keyOf(orderId, null);
		Instant now = Instant.now();
		watches.put(orderId, new Watch(key, Duration.ofSeconds(Math.max(10, intervalSec)),
				now.plus(Duration.ofMinutes(maxMin)), null, now));
		recorder.event("WATCH_START", "orderId=" + orderId + " intervalSec=" + intervalSec + " maxMin=" + maxMin);
		return Map.of("watching", orderId);
	}

	@PostMapping("/webhook-mode")
	Map<String, Object> webhookMode(@RequestParam int status) {
		webhook.setResponseStatus(status);
		recorder.event("WEBHOOK_MODE", "status=" + status);
		return Map.of("webhookResponseStatus", status);
	}

	@Scheduled(fixedDelay = 5000)
	void pollWatches() {
		Instant now = Instant.now();
		watches.forEach((orderId, w) -> {
			if (now.isBefore(w.nextAt())) {
				return;
			}
			var result = client.getPayment(w.paymentKey(), null);
			String status = match(STATUS, result.body());
			if (w.lastStatus() == null || !w.lastStatus().equals(status)) {
				recorder.event("WATCH_STATUS", "orderId=" + orderId + " status=" + status + " http=" + result.status()
						+ " file=" + result.file());
			}
			boolean done = now.isAfter(w.until()) || "EXPIRED".equals(status) || "ABORTED".equals(status)
					|| "CANCELED".equals(status);
			if (done) {
				watches.remove(orderId);
				recorder.event("WATCH_END", "orderId=" + orderId + " last=" + status);
			}
			else {
				watches.put(orderId, new Watch(w.paymentKey(), w.interval(), w.until(), status, now.plus(w.interval())));
			}
		});
	}

	private String keyOf(String orderId, String paymentKey) {
		if (paymentKey != null) {
			return paymentKey;
		}
		var order = shop.orders().get(orderId);
		if (order == null || order.paymentKey == null) {
			throw new IllegalArgumentException("paymentKey 없음: " + orderId);
		}
		return order.paymentKey;
	}

	private static Map<String, Object> summary(TossClient.Result r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("http", r.status());
		m.put("status", match(STATUS, r.body()));
		m.put("code", match(CODE, r.body()));
		m.put("elapsedMs", r.elapsedMs());
		m.put("file", r.file());
		return m;
	}

	private static String match(Pattern p, String body) {
		if (body == null) {
			return null;
		}
		Matcher m = p.matcher(body);
		return m.find() ? m.group(1) : null;
	}
}
