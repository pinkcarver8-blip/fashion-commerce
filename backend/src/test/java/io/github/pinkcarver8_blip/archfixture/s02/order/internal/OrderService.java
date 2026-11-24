package io.github.pinkcarver8_blip.archfixture.s02.order.internal;

import io.github.pinkcarver8_blip.archfixture.s02.payment.api.PaymentApi;

/** 위반: order → payment. payment → order와 합쳐 순환이 된다. */
class OrderService {

  void create(PaymentApi paymentApi) {
    paymentApi.pay();
  }
}
