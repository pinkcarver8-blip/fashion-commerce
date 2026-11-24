package io.github.pinkcarver8_blip.archfixture.s01.money;

import io.github.pinkcarver8_blip.archfixture.s01.order.api.OrderApi;

/** 위반: 공유 모듈은 어떤 업무 모듈에도 의존하지 않는다. */
public class OrderCounter {

    long count(OrderApi orderApi) {
        return orderApi.count();
    }
}
