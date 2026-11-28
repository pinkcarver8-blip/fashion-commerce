package io.github.pinkcarver8_blip.commerce.member.web;

import io.github.pinkcarver8_blip.commerce.identity.CustomerPrincipal;
import io.github.pinkcarver8_blip.commerce.member.internal.AuthService;
import io.github.pinkcarver8_blip.commerce.member.web.AuthRequests.MeResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보(T1). 회원은 요청 값이 아니라 토큰에서 나온 주체로만 정한다(S2, .claude/rules/shared-modules.md 막는 장치 2번). */
@RestController
@RequestMapping(path = "/api/members", produces = MediaType.APPLICATION_JSON_VALUE)
class MemberController {

    private final AuthService authService;

    MemberController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal CustomerPrincipal principal) {
        AuthService.MemberView member = authService.me(principal.memberId());
        return new MeResponse(member.email(), member.name());
    }
}
