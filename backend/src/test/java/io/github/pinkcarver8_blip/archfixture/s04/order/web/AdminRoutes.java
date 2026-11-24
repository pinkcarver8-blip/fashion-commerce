package io.github.pinkcarver8_blip.archfixture.s04.order.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 위반: 인터페이스에 단 관리자 경로도 Spring이 구현 클래스에 합쳐 쓴다. */
@RequestMapping("/api/admin/orders")
interface AdminRoutes {

    @GetMapping
    String list();
}
