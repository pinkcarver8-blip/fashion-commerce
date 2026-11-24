package io.github.pinkcarver8_blip.archfixture.s04.admin.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반: admin 모듈의 컨트롤러가 /api/admin 밖 경로를 연다. */
@RestController
@RequestMapping("/api/orders")
class WrongPathAdminController {

  @GetMapping
  String list() {
    return "";
  }
}
