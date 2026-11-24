package io.github.pinkcarver8_blip.archfixture.s03.order.api;

import io.github.pinkcarver8_blip.archfixture.s03.order.domain.Order;

/** 위반: 공개 API가 내부 엔티티(Order)를 그대로 돌려준다. */
public interface OrderApi {
  Order find(long orderId);
}
