package io.github.pinkcarver8_blip.archfixture.s04.catalog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반: 클래스 경로(/api)와 메서드 경로(/admin/products)를 이으면 관리자 경로가 된다. */
@RestController
@RequestMapping("/api")
class MethodLevelAdminController {

  @GetMapping("/admin/products")
  String list() {
    return "";
  }
}
