package io.github.pinkcarver8_blip.archfixture.s04.member.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 위반: /api/{section}은 /api/admin 요청과도 맞을 수 있다. */
@RestController
class PatternController {

  @GetMapping("/api/{section}/users")
  String list(@PathVariable String section) {
    return section;
  }
}
