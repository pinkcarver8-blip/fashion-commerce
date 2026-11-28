package io.github.pinkcarver8_blip.commerce.member.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 인증 요청 본문(ADR-010: DTO는 record). 권한·회원 번호 같은 칸은 두지 않는다(S2). 모르는 칸(예: {@code role})은 무시된다(G3).
 *
 * <p>비밀번호·토큰이 든 record는 toString을 가린다. 실수로 기록에 찍혀도 값이 남지 않게(X1).
 */
final class AuthRequests {

    private AuthRequests() {}

    /** 길이 상한은 거절 이유를 정확히 주려고 규칙보다 넉넉하게 둔다. 정확한 비밀번호 규칙은 PasswordPolicy가 본다. */
    record SignupRequest(
            @NotNull @Size(max = 320) String email,
            @NotNull @Size(max = 200) String password,
            @NotNull @Size(max = 200) String name) {

        @Override
        public String toString() {
            return "SignupRequest[(숨김)]";
        }
    }

    record SignupCompleteRequest(
            @NotNull @Size(max = 100) String token, @NotNull @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "SignupCompleteRequest[(숨김)]";
        }
    }

    record LoginRequest(
            @NotNull @Size(max = 320) String email, @NotNull @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "LoginRequest[(숨김)]";
        }
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {

        @Override
        public String toString() {
            return "TokenResponse[(숨김)]";
        }
    }

    record MessageResponse(String message) {}

    record MeResponse(String email, String name) {}
}
