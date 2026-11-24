package io.github.pinkcarver8_blip.archfixture.s04.order.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반: admin 모듈 밖에서 관리자 경로를 연다. */
@RestController
@RequestMapping("/api/admin/orders")
class SneakyAdminController {

  @GetMapping
  String list() {
    return "";
  }
}
