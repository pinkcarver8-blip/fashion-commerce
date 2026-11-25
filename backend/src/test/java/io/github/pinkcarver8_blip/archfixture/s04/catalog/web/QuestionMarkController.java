package io.github.pinkcarver8_blip.archfixture.s04.catalog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반: ?는 한 글자와 맞으므로 /api/admi?/x는 /api/admin/x와도 맞는다. */
@RestController
class QuestionMarkController {

    @GetMapping("/api/admi?/products")
    String list() {
        return "";
    }
}
