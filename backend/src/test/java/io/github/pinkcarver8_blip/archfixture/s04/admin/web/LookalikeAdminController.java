package io.github.pinkcarver8_blip.archfixture.s04.admin.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반: /api/admins는 보안 설정의 /api/admin/** 밖이라 고객 체인에 걸린다. */
@RestController
@RequestMapping("/api/admins")
class LookalikeAdminController {

  @GetMapping
  String list() {
    return "";
  }
}
