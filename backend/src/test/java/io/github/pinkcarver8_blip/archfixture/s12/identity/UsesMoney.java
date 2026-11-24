package io.github.pinkcarver8_blip.archfixture.s12.identity;

import io.github.pinkcarver8_blip.archfixture.s12.money.Money;

/** 위반: identity는 다른 공유 모듈을 부르지 않는다. */
class UsesMoney {

    long won(Money money) {
        return money.won();
    }
}
