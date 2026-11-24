package io.github.pinkcarver8_blip.archfixture.s01.common;

import io.github.pinkcarver8_blip.archfixture.s01.order.api.OrderApi;

/** 위반: common은 어떤 모듈에도 의존하지 않는다. */
public class OrderCounter {

  long count(OrderApi orderApi) {
    return orderApi.count();
  }
}
