package io.github.pinkcarver8blip.tossprobe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 원문 기록. out/calls(토스 호출), out/webhooks(웹훅 수신), out/events.log(시각표).
 * out/은 git에서 제외한다. 인증 헤더는 기록하지 않는다.
 */
@Component
public class Recorder {

	private static final Logger log = LoggerFactory.getLogger(Recorder.class);
	private static final DateTimeFormatter KST = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
		.withZone(ZoneId.of("Asia/Seoul"));
	private static final Path OUT = Path.of("out");

	private final AtomicInteger seq = new AtomicInteger();

	/** 시각표 한 줄. UTC와 KST를 함께 남긴다. */
	public void event(String type, String detail) {
		Instant now = Instant.now();
		String line = now + " (KST " + KST.format(now) + ") " + type + " " + detail;
		log.info("EVENT {}", line);
		append(OUT.resolve("events.log"), line + System.lineSeparator());
	}

	/** 요청 정보와 응답 원문을 JSON 한 파일로 남기고 파일 이름을 돌려준다. */
	public String call(String label, Map<String, String> request, int status, long elapsedMs,
			Map<String, String> responseHeaders, String body) {
		String name = String.format("%03d-%s-%s.json", seq.incrementAndGet(), label, status < 0 ? "ERR" : status);
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("  \"recordedAt\": ").append(q(Instant.now().toString())).append(",\n");
		sb.append("  \"label\": ").append(q(label)).append(",\n");
		sb.append("  \"request\": ").append(obj(request)).append(",\n");
		sb.append("  \"status\": ").append(status).append(",\n");
		sb.append("  \"elapsedMs\": ").append(elapsedMs).append(",\n");
		sb.append("  \"responseHeaders\": ").append(obj(responseHeaders)).append(",\n");
		sb.append("  \"body\": ").append(isJson(body) ? body.strip() : q(body)).append("\n}\n");
		write(OUT.resolve("calls").resolve(name), sb.toString());
		return name;
	}

	public String webhook(Map<String, String> headers, String body) {
		String name = String.format("%03d-webhook.json", seq.incrementAndGet());
		String json = "{\n  \"receivedAt\": " + q(Instant.now().toString()) + ",\n  \"headers\": " + obj(headers)
				+ ",\n  \"body\": " + (isJson(body) ? body.strip() : q(body)) + "\n}\n";
		write(OUT.resolve("webhooks").resolve(name), json);
		return name;
	}

	private static boolean isJson(String body) {
		if (body == null) {
			return false;
		}
		String s = body.strip();
		return s.startsWith("{") || s.startsWith("[");
	}

	private static String obj(Map<String, String> map) {
		StringBuilder sb = new StringBuilder("{");
		map.forEach((k, v) -> sb.append(sb.length() > 1 ? ", " : "").append(q(k)).append(": ").append(q(v)));
		return sb.append("}").toString();
	}

	static String q(String s) {
		if (s == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					}
					else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}

	private static void write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			log.warn("기록 실패 {}: {}", file, e.getClass().getSimpleName());
		}
	}

	private static void append(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			log.warn("기록 실패 {}: {}", file, e.getClass().getSimpleName());
		}
	}
}
