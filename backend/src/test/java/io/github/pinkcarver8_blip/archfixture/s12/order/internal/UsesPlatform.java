package io.github.pinkcarver8_blip.archfixture.s12.order.internal;

import io.github.pinkcarver8_blip.archfixture.s12.platform.WebConfig;

/** 위반: 아무 모듈도 platform을 부를 수 없다. */
class UsesPlatform {

    Class<?> config() {
        return WebConfig.class;
    }
}
