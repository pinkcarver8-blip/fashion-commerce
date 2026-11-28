package io.github.pinkcarver8_blip.commerce.member.web;

import io.github.pinkcarver8_blip.commerce.member.internal.AuthService;
import io.github.pinkcarver8_blip.commerce.member.internal.AuthSession;
import io.github.pinkcarver8_blip.commerce.member.internal.MemberErrorCode;
import io.github.pinkcarver8_blip.commerce.member.internal.MemberException;
import io.github.pinkcarver8_blip.commerce.member.internal.SignupService;
import io.github.pinkcarver8_blip.commerce.member.web.AuthRequests.LoginRequest;
import io.github.pinkcarver8_blip.commerce.member.web.AuthRequests.MessageResponse;
import io.github.pinkcarver8_blip.commerce.member.web.AuthRequests.SignupCompleteRequest;
import io.github.pinkcarver8_blip.commerce.member.web.AuthRequests.SignupRequest;
import io.github.pinkcarver8_blip.commerce.member.web.AuthRequests.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 인증 API(R1-01). 모든 주소가 비로그인 공개 경로다(platform 고객 보안 설정의 목록).
 *
 * <ul>
 *   <li>모든 요청에 {@code X-Requested-With: XMLHttpRequest} 헤더를 요구한다. 다른 사이트의 폼 전송은 이 헤더를 붙일 수
 *       없다(ADR-008 결정 3). 본문은 JSON만 받는다.
 *   <li>접속 IP는 {@link HttpServletRequest#getRemoteAddr()}다. Tomcat이 신뢰 프록시에서 온 요청만 {@code
 *       X-Forwarded-For}로 바꾼다(L5).
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class AuthController {

    static final String REQUESTED_WITH = "X-Requested-With";
    private static final String REQUESTED_WITH_VALUE = "XMLHttpRequest";

    private final SignupService signupService;
    private final AuthService authService;

    AuthController(SignupService signupService, AuthService authService) {
        this.signupService = signupService;
        this.authService = authService;
    }

    /** 가입 신청. 새 이메일이든 이미 회원인 이메일이든 같은 답이다(G5). */
    @PostMapping(path = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MessageResponse> signup(
            @RequestHeader(name = REQUESTED_WITH, required = false) String requestedWith,
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest http) {
        requireRequestedWith(requestedWith);
        signupService.requestSignup(
                request.email(), request.password(), request.name(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageResponse("인증 메일을 보냈습니다. 메일의 링크를 열어 가입을 마쳐 주세요."));
    }

    /** 메일 링크의 토큰 + 가입 때 정한 비밀번호 → 회원이 되고 바로 로그인된다(V2). */
    @PostMapping(path = "/signup/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> completeSignup(
            @RequestHeader(name = REQUESTED_WITH, required = false) String requestedWith,
            @Valid @RequestBody SignupCompleteRequest request) {
        requireRequestedWith(requestedWith);
        return withSession(signupService.completeSignup(request.token(), request.password()));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TokenResponse> login(
            @RequestHeader(name = REQUESTED_WITH, required = false) String requestedWith,
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {
        requireRequestedWith(requestedWith);
        return withSession(
                authService.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    /** 갱신. 쿠키는 쓸 때마다 새것으로 바뀐다. 거절되면 쿠키를 지운다(R1~R5). */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @RequestHeader(name = REQUESTED_WITH, required = false) String requestedWith,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        requireRequestedWith(requestedWith);
        try {
            return withSession(authService.refresh(refreshToken));
        } catch (MemberException e) {
            throw new CookieClearingException(e.code());
        }
    }

    /** 로그아웃. 그 로그인 계열을 모두 폐기하고 쿠키를 지운다. 모르는 토큰이어도 같은 답이다(R7). */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @RequestHeader(name = REQUESTED_WITH, required = false) String requestedWith,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        requireRequestedWith(requestedWith);
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.cleared().toString())
                .build();
    }

    private static ResponseEntity<TokenResponse> withSession(AuthSession session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        RefreshCookie.of(
                                        session.refreshToken(),
                                        session.refreshExpiresAt(),
                                        Instant.now())
                                .toString())
                .body(
                        new TokenResponse(
                                session.accessToken(), "Bearer", session.accessExpiresInSeconds()));
    }

    private static void requireRequestedWith(String value) {
        if (!REQUESTED_WITH_VALUE.equals(value)) {
            throw new MemberException(MemberErrorCode.REQUESTED_WITH_REQUIRED);
        }
    }

    /** 갱신이 거절되면 오류 응답에 쿠키 지우기를 함께 싣는다(MemberExceptionHandler). */
    static final class CookieClearingException extends MemberException {

        private static final long serialVersionUID = 1L;

        CookieClearingException(MemberErrorCode code) {
            super(code);
        }
    }
}
