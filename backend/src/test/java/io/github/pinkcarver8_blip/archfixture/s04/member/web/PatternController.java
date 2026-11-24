package io.github.pinkcarver8_blip.archfixture.s04.member.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 위반: /api/{section}은 /api/admin 요청과도 맞을 수 있다. */
@RestController
class PatternController {

    // 받은 값을 응답에 그대로 돌려주지 않는다(CodeQL java/xss 지적, R0-08). 경로 패턴만 보여 주는 예시다.
    @GetMapping("/api/{section}/users")
    String list(@PathVariable("section") String section) {
        return "";
    }
}
