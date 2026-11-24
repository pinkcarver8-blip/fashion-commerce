package io.github.pinkcarver8_blip.archfixture.s03.payment.internal;

import io.github.pinkcarver8_blip.archfixture.s03.order.domain.Order;

/** 위반: payment → order는 허용된 방향이지만, 공개 API가 아닌 엔티티를 직접 쓴다. */
class PaymentService {

  Long orderIdOf(Order order) {
    return order.getId();
  }
}
