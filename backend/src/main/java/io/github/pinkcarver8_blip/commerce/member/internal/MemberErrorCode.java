package io.github.pinkcarver8_blip.commerce.member.internal;

import org.springframework.http.HttpStatus;

/**
 * member 모듈의 오류 코드(ADR-010: RFC 9457 + 정해 둔 code). 메시지는 여기 적은 문장만 내보낸다. 예외 메시지·내부 정보는 싣지 않는다(S9).
 *
 * <p>로그인 실패는 이유(없는 이메일·틀린 비밀번호)를 나누지 않는다(ADR-008 결정 7).
 */
public enum MemberErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 맞지 않습니다"),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "이메일 주소 형식이 맞지 않습니다"),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "이름은 1~50자로 입력해 주세요"),
    PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이어야 합니다"),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "비밀번호가 너무 깁니다(영문 72자, 한글 24자까지)"),
    PASSWORD_TOO_COMMON(HttpStatus.BAD_REQUEST, "많이 쓰여 추측하기 쉬운 비밀번호입니다. 다른 비밀번호를 써 주세요"),
    PASSWORD_CONTAINS_CONTEXT(HttpStatus.BAD_REQUEST, "비밀번호에 이메일 아이디나 서비스 이름을 넣지 마세요"),
    PASSWORD_REPETITIVE(HttpStatus.BAD_REQUEST, "같은 글자 반복이나 연속된 글자(12345678, abcdefgh)는 쓸 수 없습니다"),
    SIGNUP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "가입 요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요"),
    SIGNUP_LINK_INVALID(HttpStatus.BAD_REQUEST, "인증 링크가 맞지 않거나 만료됐습니다. 다시 가입해 주세요"),
    SIGNUP_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "가입할 때 정한 비밀번호와 다릅니다"),
    SIGNUP_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 가입이 끝난 이메일입니다. 로그인해 주세요"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않습니다"),
    LOGIN_TEMPORARILY_BLOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 15분 뒤 다시 시도해 주세요"),
    REFRESH_REJECTED(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),
    REQUESTED_WITH_REQUIRED(HttpStatus.FORBIDDEN, "허용되지 않은 요청입니다"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다"),
    MAIL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요");

    private final HttpStatus status;
    private final String message;

    MemberErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
