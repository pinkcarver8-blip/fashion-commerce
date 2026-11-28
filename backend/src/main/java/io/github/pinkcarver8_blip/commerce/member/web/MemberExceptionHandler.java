package io.github.pinkcarver8_blip.commerce.member.web;

import io.github.pinkcarver8_blip.commerce.member.internal.MemberErrorCode;
import io.github.pinkcarver8_blip.commerce.member.internal.MemberException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * member 모듈 오류 응답(RFC 9457 + code, ADR-010). 정해 둔 코드와 문장만 내보낸다. 어느 칸이 왜 틀렸는지 같은 검사 세부와 예외 메시지는 싣지
 * 않는다(S9). 앱 전체 오류 처리기는 R1-03(platform)에서 만든다.
 */
@RestControllerAdvice(assignableTypes = {AuthController.class, MemberController.class})
class MemberExceptionHandler {

    @ExceptionHandler(MemberException.class)
    ResponseEntity<ProblemDetail> handle(MemberException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.code().status());
        if (e instanceof AuthController.CookieClearingException) {
            builder.header(HttpHeaders.SET_COOKIE, RefreshCookie.cleared().toString());
        }
        return builder.contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem(e.code()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<ProblemDetail> handleBadRequest(Exception e) {
        MemberErrorCode code = MemberErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(code));
    }

    private static ProblemDetail problem(MemberErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.message());
        problem.setTitle(code.message());
        problem.setProperty("code", code.name());
        return problem;
    }
}
