package io.github.pinkcarver8_blip.archfixture.s12.money;

import io.github.pinkcarver8_blip.archfixture.s12.order.api.OrderApi;

/** 위반: 공유 모듈이 업무 모듈을 부른다. */
class MoneyToOrder {

  long count(OrderApi orderApi) {
    return orderApi.count();
  }
}
