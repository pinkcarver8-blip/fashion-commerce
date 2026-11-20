package io.github.pinkcarver8blip.tossprobe;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 화면이 쓰는 공개 API. 시크릿 키를 쓰지 않는다(터널로 열려도 되는 범위).
 */
@RestController
@RequestMapping("/api")
public class ShopController {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Pattern SAFE_ORDER_ID = Pattern.compile("[A-Za-z0-9_=-]{1,100}");
	private static final Pattern ANY_ORDER_ID = Pattern.compile("[\\x21-\\x7e]{1,100}");

	record Product(long id, String name, long price) {
	}

	/**
	 * orderId를 비우면 무작위로 만든다. orderId 규칙 확인용으로만 직접 지정한다.
	 * channel: "window"면 결제창(API 개별 연동 키), 그 밖에는 결제위젯.
	 */
	record CreateOrderRequest(long productId, int quantity, String orderId, String channel) {
	}

	record AuthResult(String orderId, String paymentKey, long amount, String paymentType) {
	}

	record PageEvent(String orderId, String type, String detail) {
	}

	public static final class Order {
		public final String orderId;
		public final String orderName;
		public final long amount;
		/** true면 결제창 주문(API 개별 연동 키로 승인·취소) */
		public final boolean api;
		public final Instant createdAt = Instant.now();
		public volatile String paymentKey;
		public volatile Long redirectedAmount;
		public volatile Instant authAt;

		Order(String orderId, String orderName, long amount, boolean api) {
			this.orderId = orderId;
			this.orderName = orderName;
			this.amount = amount;
			this.api = api;
		}
	}

	private final List<Product> products = List.of(new Product(1, "옥스포드 셔츠", 39000),
			new Product(2, "와이드 데님", 59000), new Product(3, "울 니트 머플러", 25000));
	private final Map<String, Order> orders = new ConcurrentHashMap<>();
	private final TossProperties toss;
	private final Recorder recorder;

	public ShopController(TossProperties toss, Recorder recorder) {
		this.toss = toss;
		this.recorder = recorder;
	}

	public Map<String, Order> orders() {
		return orders;
	}

	@GetMapping("/products")
	List<Product> products() {
		return products;
	}

	/** 클라이언트 키는 브라우저에 공개되는 키다. 시크릿 키는 내보내지 않는다. */
	@GetMapping("/config")
	Map<String, String> config() {
		return Map.of("clientKey", toss.clientKey(), "apiClientKey", toss.hasApiKeys() ? toss.apiClientKey() : "");
	}

	@PostMapping("/orders")
	ResponseEntity<?> create(@RequestBody CreateOrderRequest request) {
		Product p = products.stream().filter(x -> x.id() == request.productId()).findFirst().orElse(null);
		if (p == null || request.quantity() < 1 || request.quantity() > 10) {
			return ResponseEntity.badRequest().body(Map.of("code", "INVALID_LINE"));
		}
		String orderId = request.orderId() == null || request.orderId().isBlank() ? newOrderId() : request.orderId();
		// 규칙 위반 orderId도 토스 반응을 보려고 받는다. 다만 출력 가능한 ASCII만 허용한다
		if (!ANY_ORDER_ID.matcher(orderId).matches() || orders.containsKey(orderId)) {
			return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ORDER_ID"));
		}
		boolean api = "window".equals(request.channel());
		if (api && !toss.hasApiKeys()) {
			return ResponseEntity.badRequest().body(Map.of("code", "NO_API_KEYS"));
		}
		var order = new Order(orderId, p.name() + (request.quantity() > 1 ? " " + request.quantity() + "개" : ""),
				p.price() * request.quantity(), api);
		orders.put(orderId, order);
		recorder.event("ORDER_CREATED", "orderId=" + orderId + " channel=" + (api ? "window" : "widget") + " amount="
				+ order.amount + " len=" + orderId.length()
				+ (SAFE_ORDER_ID.matcher(orderId).matches() ? "" : " (규칙 밖 문자 포함)"));
		return ResponseEntity.ok(view(order));
	}

	@GetMapping("/orders/{orderId}")
	ResponseEntity<?> get(@PathVariable String orderId) {
		Order order = orders.get(orderId);
		return order == null ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "ORDER_NOT_FOUND"))
				: ResponseEntity.ok(view(order));
	}

	/** 화면에서 일어난 일(결제창 열기, SDK 오류 등)을 시각표에 남긴다. */
	@PostMapping("/page-events")
	ResponseEntity<?> pageEvent(@RequestBody PageEvent event) {
		if (event.orderId() == null || !orders.containsKey(event.orderId()) || event.type() == null
				|| !event.type().matches("[A-Z_]{1,40}")) {
			return ResponseEntity.badRequest().build();
		}
		String detail = event.detail() == null ? "" : event.detail().replaceAll("[\\r\\n]", " ");
		recorder.event("PAGE_" + event.type(),
				"orderId=" + event.orderId() + " " + detail.substring(0, Math.min(200, detail.length())));
		return ResponseEntity.noContent().build();
	}

	/** 성공 URL로 돌아온 값을 서버에 남긴다. 승인은 하지 않는다(승인 시점은 /probe/confirm으로 정한다). */
	@PostMapping("/auth-result")
	ResponseEntity<?> authResult(@RequestBody AuthResult result) {
		Order order = result.orderId() == null ? null : orders.get(result.orderId());
		if (order == null || result.paymentKey() == null || result.paymentKey().length() > 200) {
			return ResponseEntity.badRequest().body(Map.of("code", "ORDER_NOT_FOUND"));
		}
		if (order.paymentKey == null) {
			order.paymentKey = result.paymentKey();
			order.redirectedAmount = result.amount();
			order.authAt = Instant.now();
		}
		recorder.event("AUTH_REDIRECT", "orderId=" + order.orderId + " paymentType=" + result.paymentType()
				+ " amount=" + result.amount() + " 저장금액=" + order.amount + " paymentKeyLen="
				+ result.paymentKey().length());
		return ResponseEntity.ok(Map.of("amountMatches", order.amount == result.amount()));
	}

	static Map<String, Object> view(Order o) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("orderId", o.orderId);
		m.put("orderName", o.orderName);
		m.put("amount", o.amount);
		m.put("channel", o.api ? "window" : "widget");
		return m;
	}

	private static String newOrderId() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
