package io.github.pinkcarver8blip.tossprobe;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 시크릿 키를 쓰는 /probe/**는 이 PC에서 직접 온 요청만 받는다.
 * ngrok도 localhost로 들어오므로 주소만으로는 구분할 수 없다. 프록시 헤더와 Host까지 본다.
 */
@Component
public class LocalOnlyFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(LocalOnlyFilter.class);
	private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
	private static final String[] PROXY_HEADERS = { "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto",
			"Forwarded", "X-Real-IP" };

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/probe/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (isDirectLocal(request)) {
			chain.doFilter(request, response);
			return;
		}
		log.warn("/probe 외부 접근 차단 uri={}", request.getRequestURI());
		response.sendError(HttpServletResponse.SC_FORBIDDEN);
	}

	private static boolean isDirectLocal(HttpServletRequest request) {
		if (!LOOPBACK.contains(request.getRemoteAddr())) {
			return false;
		}
		for (String header : PROXY_HEADERS) {
			if (request.getHeader(header) != null) {
				return false;
			}
		}
		String host = request.getHeader("Host");
		return host != null && (host.startsWith("localhost:") || host.startsWith("127.0.0.1:"));
	}
}
