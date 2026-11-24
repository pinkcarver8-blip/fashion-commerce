package io.github.pinkcarver8_blip.archfixture.s02.payment.internal;

import io.github.pinkcarver8_blip.archfixture.s02.order.api.OrderApi;

class PaymentService {

  void pay(OrderApi orderApi) {
    orderApi.create();
  }
}
