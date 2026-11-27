package io.github.pinkcarver8_blip.archfixture.s13.member.web;

import io.github.pinkcarver8_blip.archfixture.s13.identity.CustomerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반 하나(forgotten)와 허용 하나(annotated): 표시를 빠뜨리면 Spring MVC가 요청 값으로 주체를 채운다. */
@RestController
class MeController {

    @GetMapping("/api/me-forgotten")
    String forgotten(CustomerPrincipal principal) {
        return principal.memberId().toString();
    }

    @GetMapping("/api/me")
    String annotated(@AuthenticationPrincipal CustomerPrincipal principal) {
        return principal.memberId().toString();
    }
}
