package io.github.pinkcarver8_blip.archfixture.s12.error;

import org.springframework.transaction.annotation.Transactional;

/** 위반: 공유 모듈에 @Transactional. */
class ErrorLog {

    @Transactional
    void save() {}
}
