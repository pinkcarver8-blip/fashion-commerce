package io.github.pinkcarver8blip.tossprobe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스 웹훅 수신(터널로 공개). 내용은 믿지 않고 기록만 한다. 확인은 paymentKey 재조회로 한다.
 * 응답 코드는 /probe/webhook-mode로 바꿔 재전송 동작을 본다.
 */
@RestController
public class WebhookController {

	private final Recorder recorder;
	private volatile int responseStatus = 200;

	public WebhookController(Recorder recorder) {
		this.recorder = recorder;
	}

	void setResponseStatus(int status) {
		this.responseStatus = status;
	}

	@PostMapping("/webhooks/toss")
	ResponseEntity<Void> receive(HttpServletRequest request, @RequestBody(required = false) String body) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (String name : Collections.list(request.getHeaderNames())) {
			// 터널이 붙이는 접속자 정보는 남기지 않는다
			if (!name.equalsIgnoreCase("x-forwarded-for") && !name.equalsIgnoreCase("authorization")
					&& !name.equalsIgnoreCase("cookie")) {
				headers.put(name, request.getHeader(name));
			}
		}
		String file = recorder.webhook(headers, body);
		recorder.event("WEBHOOK_RECEIVED", "file=" + file + " respond=" + responseStatus);
		return ResponseEntity.status(responseStatus).build();
	}
}
