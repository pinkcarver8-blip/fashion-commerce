package io.github.pinkcarver8_blip.archfixture.s01.inventory.internal;

import io.github.pinkcarver8_blip.archfixture.s01.order.api.OrderApi;

/** 위반: inventory → order는 ADR-001 목록에 없다(inventory는 다른 모듈을 부르지 않는다). */
class StockService {

  long countOrders(OrderApi orderApi) {
    return orderApi.count();
  }
}
