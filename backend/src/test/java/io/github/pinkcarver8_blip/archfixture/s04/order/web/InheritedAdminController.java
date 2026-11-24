package io.github.pinkcarver8_blip.archfixture.s04.order.web;

import org.springframework.web.bind.annotation.RestController;

/** 위반: 경로를 인터페이스에서 물려받아 admin 밖에서 관리자 경로를 연다. */
@RestController
class InheritedAdminController implements AdminRoutes {

    @Override
    public String list() {
        return "";
    }
}
