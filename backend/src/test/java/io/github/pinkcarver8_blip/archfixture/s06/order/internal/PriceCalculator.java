package io.github.pinkcarver8_blip.archfixture.s06.order.internal;

import java.math.BigDecimal;

/** 위반: 소수 타입을 필드·매개변수·반환·호출에 쓴다. */
class PriceCalculator {

  private double discountRate;

  private Float boxedRate;

  BigDecimal total(BigDecimal price) {
    return price;
  }

  long discounted(long price) {
    return Math.round(price * 0.9);
  }

  String label(long price) {
    return BigDecimal.valueOf(price).toPlainString();
  }
}
